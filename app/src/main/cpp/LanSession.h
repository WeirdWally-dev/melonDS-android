#ifndef LANSESSION_H
#define LANSESSION_H

#include <mutex>
#include <string>
#include <vector>
#include "types.h"

/**
 * Manages a melonDS LAN multiplayer session on Android.
 *
 * The desktop frontend calls MPInterface::Process() from its emulation thread even when no game is running, which is
 * what keeps the lobby alive (accepting players, sending discovery beacons, answering pings). On Android the emulation
 * thread only exists while a game is running, so this module owns a small "pump" thread that calls Process() whenever
 * the frame loop has not done so recently (lobby screen, emulator paused or closed).
 *
 * All access to the MP interface (frame loop, pump thread, JNI calls) is serialized through mpMutex().
 */
namespace LanSession
{
    enum class Mode
    {
        None = 0,
        Browsing = 1,
        Hosting = 2,
        Client = 3,
    };

    struct DiscoveredSession
    {
        std::string address;
        std::string name;
        int playerCount;
        int maxPlayers;
        bool isPlaying;
    };

    struct SessionPlayer
    {
        int id;
        std::string name;
        int status;
        std::string address;
        bool isLocal;
        int ping;
    };

    /**
     * Recursive because the core can call Platform::MP_Begin()/MP_End() from inside the frame loop, which already holds
     * this lock.
     */
    std::recursive_mutex& mpMutex();

    /**
     * Must be called by the frame loop, while holding mpMutex(), every time it calls MPInterface::Process().
     */
    void markFrameProcessed();

    Mode getMode();
    bool isLanActive();

    bool startDiscovery();
    void endDiscovery();
    bool startHost(const std::string& playerName, int maxPlayers);
    bool startClient(const std::string& playerName, const std::string& hostAddress);
    void endSession();

    std::vector<DiscoveredSession> getDiscoveredSessions();
    std::vector<SessionPlayer> getPlayers();
    int getMaxPlayers();
}

#endif //LANSESSION_H
