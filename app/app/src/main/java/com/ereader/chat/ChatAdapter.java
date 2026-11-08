package com.ereader.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息列表适配器。墨水屏友好：黑白高对比，无动画。
 */
public class ChatAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }

    public void add(ChatMessage msg) {
        messages.add(msg);
        notifyDataSetChanged();
    }

    public ChatMessage getLast() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public List<ChatMessage> getAll() {
        return messages;
    }

    public void notifyChanged() {
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Object getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).role == ChatMessage.ROLE_USER ? 0 : 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatMessage msg = messages.get(position);
        boolean isUser = msg.role == ChatMessage.ROLE_USER;
        int layoutId = isUser ? R.layout.item_message_user : R.layout.item_message_assistant;

        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(layoutId, parent, false);
            holder = new ViewHolder();
            holder.text = (TextView) convertView.findViewById(R.id.message_text);
            holder.role = (TextView) convertView.findViewById(R.id.message_role);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.text.setText(msg.content);
        holder.role.setText(isUser ? "You" : "AI");

        return convertView;
    }

    private static class ViewHolder {
        TextView text;
        TextView role;
    }
}
