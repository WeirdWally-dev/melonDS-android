#include <jni.h>
#include <string>
#include "LanSession.h"

namespace
{
    std::string toStdString(JNIEnv* env, jstring string)
    {
        if (string == nullptr)
            return "";

        const char* chars = env->GetStringUTFChars(string, nullptr);
        std::string result(chars);
        env->ReleaseStringUTFChars(string, chars);
        return result;
    }

    // Player names are limited to 31 bytes by the LAN protocol. Avoid cutting a UTF-8 sequence in half.
    std::string truncatePlayerName(const std::string& name)
    {
        if (name.size() <= 31)
            return name;

        size_t end = 31;
        while (end > 0 && (static_cast<unsigned char>(name[end]) & 0xC0) == 0x80)
            end--;

        return name.substr(0, end);
    }

    // NewStringUTF expects modified UTF-8 and aborts on malformed input, which a remote player could send. Keep valid
    // 1 to 3 byte sequences (identical in UTF-8 and modified UTF-8) and replace everything else (control characters,
    // 4 byte sequences, garbage) with '?'.
    jstring toSafeJString(JNIEnv* env, const std::string& string)
    {
        std::string sanitized;
        sanitized.reserve(string.size());

        size_t i = 0;
        while (i < string.size())
        {
            auto lead = static_cast<unsigned char>(string[i]);
            size_t length;
            unsigned int codepoint;

            if (lead < 0x80) { length = 1; codepoint = lead; }
            else if ((lead & 0xE0) == 0xC0) { length = 2; codepoint = lead & 0x1F; }
            else if ((lead & 0xF0) == 0xE0) { length = 3; codepoint = lead & 0x0F; }
            else { sanitized.push_back('?'); i++; continue; }

            bool valid = i + length <= string.size();
            for (size_t j = 1; valid && j < length; j++)
            {
                auto continuation = static_cast<unsigned char>(string[i + j]);
                if ((continuation & 0xC0) != 0x80)
                    valid = false;
                else
                    codepoint = (codepoint << 6) | (continuation & 0x3F);
            }

            // Reject overlong encodings, UTF-16 surrogates and control characters
            if (valid)
            {
                if ((length == 2 && codepoint < 0x80) || (length == 3 && codepoint < 0x800) || (codepoint >= 0xD800 && codepoint <= 0xDFFF) || codepoint < 0x20 || codepoint == 0x7F)
                    valid = false;
            }

            if (valid)
            {
                sanitized.append(string, i, length);
                i += length;
            }
            else
            {
                sanitized.push_back('?');
                i++;
            }
        }

        return env->NewStringUTF(sanitized.c_str());
    }
}

extern "C"
{

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonLan_startDiscovery(JNIEnv* env, jobject thiz)
{
    return LanSession::startDiscovery();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonLan_endDiscovery(JNIEnv* env, jobject thiz)
{
    LanSession::endDiscovery();
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonLan_startHost(JNIEnv* env, jobject thiz, jstring playerName, jint maxPlayers)
{
    return LanSession::startHost(truncatePlayerName(toStdString(env, playerName)), maxPlayers);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonLan_startClient(JNIEnv* env, jobject thiz, jstring playerName, jstring hostAddress)
{
    return LanSession::startClient(truncatePlayerName(toStdString(env, playerName)), toStdString(env, hostAddress));
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonLan_endSession(JNIEnv* env, jobject thiz)
{
    LanSession::endSession();
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonLan_getMode(JNIEnv* env, jobject thiz)
{
    return static_cast<jint>(LanSession::getMode());
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonLan_getMaxPlayers(JNIEnv* env, jobject thiz)
{
    return LanSession::getMaxPlayers();
}

JNIEXPORT jobjectArray JNICALL
Java_me_magnum_melonds_MelonLan_getDiscoveredSessions(JNIEnv* env, jobject thiz)
{
    auto sessions = LanSession::getDiscoveredSessions();

    jclass sessionClass = env->FindClass("me/magnum/melonds/domain/model/lan/LanDiscoveredSession");
    jmethodID constructor = env->GetMethodID(sessionClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;IIZ)V");
    jobjectArray result = env->NewObjectArray((jsize) sessions.size(), sessionClass, nullptr);

    for (size_t i = 0; i < sessions.size(); i++)
    {
        const auto& session = sessions[i];
        jstring address = env->NewStringUTF(session.address.c_str());
        jstring name = toSafeJString(env, session.name);
        jobject object = env->NewObject(sessionClass, constructor, address, name, session.playerCount, session.maxPlayers, (jboolean) session.isPlaying);
        env->SetObjectArrayElement(result, (jsize) i, object);
        env->DeleteLocalRef(object);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(address);
    }

    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_me_magnum_melonds_MelonLan_getPlayers(JNIEnv* env, jobject thiz)
{
    auto players = LanSession::getPlayers();

    jclass playerClass = env->FindClass("me/magnum/melonds/domain/model/lan/LanPlayer");
    jmethodID constructor = env->GetMethodID(playerClass, "<init>", "(ILjava/lang/String;ILjava/lang/String;ZI)V");
    jobjectArray result = env->NewObjectArray((jsize) players.size(), playerClass, nullptr);

    for (size_t i = 0; i < players.size(); i++)
    {
        const auto& player = players[i];
        jstring name = toSafeJString(env, player.name);
        jstring address = env->NewStringUTF(player.address.c_str());
        jobject object = env->NewObject(playerClass, constructor, player.id, name, player.status, address, (jboolean) player.isLocal, player.ping);
        env->SetObjectArrayElement(result, (jsize) i, object);
        env->DeleteLocalRef(object);
        env->DeleteLocalRef(address);
        env->DeleteLocalRef(name);
    }

    return result;
}

}
