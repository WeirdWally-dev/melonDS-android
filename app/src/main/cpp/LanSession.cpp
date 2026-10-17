#include "LanSession.h"

#include <atomic>
#include <chrono>
#include <thread>
#include <cstdio>
#include <cstring>
#include "net/MPInterface.h"
#include "net/LAN.h"
#include "Platform.h"

using namespace melonDS;

namespace LanSession
{
    namespace
    {
        // How long the frame loop may go without processing the MP interface before the pump thread takes over
        constexpr std::chrono::milliseconds kFrameLoopIdleThreshold(200);
        constexpr std::chrono::milliseconds kPumpInterval(16);

        std::recursive_mutex sessionMutex;
        std::atomic<Mode> currentMode(Mode::None);
        std::atomic<int64_t> lastFrameProcessedMs(0);

        std::mutex pumpControlMutex;
        std::thread pumpThread;
        std::atomic_bool pumpRunning(false);

        int64_t nowMs()
        {
            using namespace std::chrono;
            return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
        }

        LAN& lan()
        {
            return (LAN&) MPInterface::Get();
        }

        void ensureLanInterface()
        {
            if (MPInterface::GetType() != MPInterface_LAN)
                MPInterface::Set(MPInterface_LAN);
        }

        void resetInterface()
        {
            if (MPInterface::GetType() != MPInterface_Dummy)
                MPInterface::Set(MPInterface_Dummy);
        }

        void pumpLoop()
        {
            while (pumpRunning.load())
            {
                std::this_thread::sleep_for(kPumpInterval);

                if (nowMs() - lastFrameProcessedMs.load() < kFrameLoopIdleThreshold.count())
                    continue;

                // try_lock: if a JNI call (e.g. a 5 second client connect) or the frame loop holds the lock, just skip
                // this tick instead of blocking. This also guarantees that stopPump() can join without deadlocking.
                std::unique_lock<std::recursive_mutex> lock(sessionMutex, std::try_to_lock);
                if (!lock.owns_lock())
                    continue;

                if (currentMode.load() != Mode::None && MPInterface::GetType() == MPInterface_LAN)
                    MPInterface::Get().Process();
            }
        }

        void startPump()
        {
            std::lock_guard<std::mutex> control(pumpControlMutex);
            if (pumpRunning.load())
                return;

            pumpRunning = true;
            pumpThread = std::thread(pumpLoop);
        }

        // Must NOT be called while holding sessionMutex
        void stopPump()
        {
            std::lock_guard<std::mutex> control(pumpControlMutex);
            if (!pumpRunning.load())
                return;

            pumpRunning = false;
            if (pumpThread.joinable())
                pumpThread.join();
        }

        // Discovery keys are host-order IPv4 addresses
        std::string formatHostOrderAddress(u32 address)
        {
            char buffer[16];
            snprintf(buffer, sizeof(buffer), "%u.%u.%u.%u", address >> 24, (address >> 16) & 0xFF, (address >> 8) & 0xFF, address & 0xFF);
            return buffer;
        }

        // Player addresses come straight from ENet, in network order
        std::string formatNetworkOrderAddress(u32 address)
        {
            char buffer[16];
            snprintf(buffer, sizeof(buffer), "%u.%u.%u.%u", address & 0xFF, (address >> 8) & 0xFF, (address >> 16) & 0xFF, address >> 24);
            return buffer;
        }
    }

    std::recursive_mutex& mpMutex()
    {
        return sessionMutex;
    }

    void markFrameProcessed()
    {
        lastFrameProcessedMs = nowMs();
    }

    Mode getMode()
    {
        return currentMode.load();
    }

    bool isLanActive()
    {
        Mode mode = currentMode.load();
        return mode == Mode::Hosting || mode == Mode::Client;
    }

    bool startDiscovery()
    {
        {
            std::lock_guard<std::recursive_mutex> lock(sessionMutex);
            if (currentMode.load() != Mode::None)
                return currentMode.load() == Mode::Browsing;

            ensureLanInterface();
            if (!lan().StartDiscovery())
            {
                Platform::Log(Platform::LogLevel::Error, "LAN: failed to start discovery\n");
                resetInterface();
                return false;
            }

            currentMode = Mode::Browsing;
        }

        startPump();
        return true;
    }

    void endDiscovery()
    {
        {
            std::lock_guard<std::recursive_mutex> lock(sessionMutex);
            if (currentMode.load() != Mode::Browsing)
                return;

            lan().EndDiscovery();
            resetInterface();
            currentMode = Mode::None;
        }

        stopPump();
    }

    bool startHost(const std::string& playerName, int maxPlayers)
    {
        bool success;
        {
            std::lock_guard<std::recursive_mutex> lock(sessionMutex);
            Mode mode = currentMode.load();
            if (mode == Mode::Hosting || mode == Mode::Client)
                return false;

            ensureLanInterface();
            if (mode == Mode::Browsing)
                lan().EndDiscovery();

            success = lan().StartHost(playerName.c_str(), maxPlayers);
            if (success)
            {
                currentMode = Mode::Hosting;
            }
            else
            {
                Platform::Log(Platform::LogLevel::Error, "LAN: failed to start host\n");
                resetInterface();
                currentMode = Mode::None;
            }
        }

        if (success)
            startPump();
        else
            stopPump();

        return success;
    }

    bool startClient(const std::string& playerName, const std::string& hostAddress)
    {
        bool success;
        {
            // This blocks for up to 5 seconds while ENet connects. The pump thread skips its ticks meanwhile.
            std::lock_guard<std::recursive_mutex> lock(sessionMutex);
            Mode mode = currentMode.load();
            if (mode == Mode::Hosting || mode == Mode::Client)
                return false;

            ensureLanInterface();
            if (mode == Mode::Browsing)
                lan().EndDiscovery();

            success = lan().StartClient(playerName.c_str(), hostAddress.c_str());
            if (success)
            {
                currentMode = Mode::Client;
            }
            else
            {
                Platform::Log(Platform::LogLevel::Error, "LAN: failed to connect to %s\n", hostAddress.c_str());
                if (mode == Mode::Browsing && lan().StartDiscovery())
                {
                    // Go back to browsing so the user can pick another session
                    currentMode = Mode::Browsing;
                }
                else
                {
                    resetInterface();
                    currentMode = Mode::None;
                }
            }
        }

        if (currentMode.load() == Mode::None)
            stopPump();
        else
            startPump();

        return success;
    }

    void endSession()
    {
        {
            std::lock_guard<std::recursive_mutex> lock(sessionMutex);
            Mode mode = currentMode.load();
            if (mode == Mode::None)
                return;

            if (mode == Mode::Browsing)
                lan().EndDiscovery();
            else
                lan().EndSession();

            resetInterface();
            currentMode = Mode::None;
        }

        stopPump();
    }

    std::vector<DiscoveredSession> getDiscoveredSessions()
    {
        std::vector<DiscoveredSession> sessions;

        std::lock_guard<std::recursive_mutex> lock(sessionMutex);
        if (currentMode.load() != Mode::Browsing || MPInterface::GetType() != MPInterface_LAN)
            return sessions;

        for (const auto& [address, data] : lan().GetDiscoveryList())
        {
            sessions.push_back({
                formatHostOrderAddress(address),
                std::string(data.SessionName, strnlen(data.SessionName, sizeof(data.SessionName))),
                data.NumPlayers,
                data.MaxPlayers,
                data.Status == 1,
            });
        }

        return sessions;
    }

    std::vector<SessionPlayer> getPlayers()
    {
        std::vector<SessionPlayer> players;

        std::lock_guard<std::recursive_mutex> lock(sessionMutex);
        if (!isLanActive() || MPInterface::GetType() != MPInterface_LAN)
            return players;

        for (const auto& player : lan().GetPlayerList())
        {
            players.push_back({
                player.ID,
                std::string(player.Name, strnlen(player.Name, sizeof(player.Name))),
                (int) player.Status,
                player.IsLocalPlayer ? "" : formatNetworkOrderAddress(player.Address),
                player.IsLocalPlayer,
                (int) player.Ping,
            });
        }

        return players;
    }

    int getMaxPlayers()
    {
        std::lock_guard<std::recursive_mutex> lock(sessionMutex);
        if (!isLanActive() || MPInterface::GetType() != MPInterface_LAN)
            return 0;

        return lan().GetMaxPlayers();
    }
}
