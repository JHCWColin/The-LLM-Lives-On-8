package com.ereader.chat;

/**
 * 一条聊天消息。
 */
public class ChatMessage {
    public static final int ROLE_USER = 0;
    public static final int ROLE_ASSISTANT = 1;

    public final int role;
    public String content;
    /** 是否正在流式生成中 */
    public boolean streaming;

    public ChatMessage(int role, String content) {
        this.role = role;
        this.content = content == null ? "" : content;
        this.streaming = false;
    }
}
