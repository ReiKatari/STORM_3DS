package com.storm3ds.emulator.nativebridge.utils

import androidx.annotation.Keep

@Keep
object NetPlayManager {
    external fun netPlayCreateRoom(
        ipAddress: String,
        port: Int,
        username: String,
        preferedGameName: String,
        preferredGameId: Long,
        password: String,
        roomName: String,
        maxPlayers: Int
    ): Int

    external fun netPlayJoinRoom(
        ipAddress: String,
        port: Int,
        username: String,
        password: String
    ): Int

    external fun netPlayRoomInfo(): Array<String>
    external fun netPlayIsJoined(): Boolean
    external fun netPlayIsHostedRoom(): Boolean
    external fun netPlaySendMessage(msg: String)
    external fun netPlayKickUser(username: String)
    external fun netPlayLeaveRoom()
    external fun netPlayIsModerator(): Boolean
    external fun netPlayGetBanList(): Array<String>
    external fun netPlayBanUser(username: String)
    external fun netPlayUnbanUser(username: String)
    external fun netPlayGetPublicRooms(): Array<String>

    data class RoomInfo(
        val name: String,
        val hasPassword: Boolean,
        val maxPlayers: Int,
        val ip: String,
        val port: Int,
        val description: String,
        val owner: String,
        val preferredGameId: Long,
        val preferredGameName: String,
        val members: MutableList<RoomMember> = mutableListOf()
    )

    data class RoomMember(
        val username: String,
        val nickname: String,
        val gameId: Long,
        val gameName: String
    )

    private var messageListener: ((Int, String) -> Unit)? = null
    val chatMessages = mutableListOf<Pair<Int, String>>()

    fun setOnMessageReceivedListener(listener: ((Int, String) -> Unit)?) {
        messageListener = listener
    }

    @JvmStatic
    fun addNetPlayMessage(type: Int, msg: String) {
        synchronized(chatMessages) {
            chatMessages.add(type to msg)
            if (chatMessages.size > 200) {
                chatMessages.removeAt(0)
            }
        }
        messageListener?.invoke(type, msg)
    }

    @JvmStatic
    fun clearChat() {
        synchronized(chatMessages) {
            chatMessages.clear()
        }
    }

    fun getPublicRooms(): List<RoomInfo> {
        val roomData = runCatching { netPlayGetPublicRooms() }.getOrElse { return emptyList() }
        val rooms = mutableMapOf<String, RoomInfo>()
        for (data in roomData) {
            val parts = data.split("|")
            if (parts.firstOrNull() == "MEMBER" && parts.size >= 6) {
                val roomName = parts[1]
                rooms[roomName]?.members?.add(
                    RoomMember(
                        username = parts[2],
                        nickname = parts[3],
                        gameId = parts[4].toLongOrNull() ?: 0L,
                        gameName = parts[5]
                    )
                )
            } else if (parts.size >= 8) {
                val name = parts[0]
                rooms[name] = RoomInfo(
                    name = name,
                    hasPassword = parts[1].toBoolean(),
                    maxPlayers = parts[2].toIntOrNull() ?: 4,
                    ip = parts[3],
                    port = parts[4].toIntOrNull() ?: 24872,
                    description = parts[5],
                    owner = parts[6],
                    preferredGameId = parts[7].toLongOrNull() ?: 0L,
                    preferredGameName = parts.getOrElse(8) { "" }
                )
            }
        }
        return rooms.values.toList()
    }
}
