package com.ereader.chat;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 墨水屏本地大模型聊天主界面。
 * 设计要点：
 *  - 黑白高对比、大字号、无动画（适配 E-Ink 刷新与残影）
 *  - 模型为本地 GGUF（SmolLM2-135M），纯离线推理
 *  - 流式输出做节流刷新，避免频繁触发墨水屏刷新
 */
public class MainActivity extends Activity implements LlamaModel.Listener {

    private static final String MODEL_ASSET = "models/SmolLM2-135M-Instruct-Q4_K_M.gguf";
    private static final String MODEL_FILE = "model.gguf";
    private static final int N_CTX = 512;
    private static final int N_THREADS = 4;
    private static final int MAX_TOKENS = 256;
    private static final int MAX_HISTORY_MESSAGES = 6;

    private static final String SYSTEM_PROMPT =
            "You are a helpful, friendly AI assistant running on an e-ink reader. " +
            "Keep your answers concise and clear.";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private final AtomicBoolean modelLoaded = new AtomicBoolean(false);

    private final StringBuilder pending = new StringBuilder();
    private long lastRefresh = 0;

    private ListView listView;
    private ChatAdapter adapter;
    private EditText input;
    private Button sendButton;
    private Button stopButton;
    private TextView statusBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.chat_list);
        input = (EditText) findViewById(R.id.input);
        sendButton = (Button) findViewById(R.id.send_button);
        stopButton = (Button) findViewById(R.id.stop_button);
        statusBar = (TextView) findViewById(R.id.status_bar);

        adapter = new ChatAdapter(this);
        listView.setAdapter(adapter);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
        listView.setDivider(null);
        listView.setDividerHeight(0);

        sendButton.setOnClickListener(v -> sendMessage());
        stopButton.setOnClickListener(v -> stopGeneration());
        stopButton.setEnabled(false);

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendMessage();
                return true;
            }
            return false;
        });

        setStatus("Loading model...");
        executor.execute(this::prepareAndLoadModel);
    }

    private void setStatus(String text) {
        handler.post(() -> statusBar.setText(text));
    }

    /** 复制内置模型到内部存储，并加载。 */
    private void prepareAndLoadModel() {
        try {
            File modelFile = new File(getFilesDir(), MODEL_FILE);
            if (!modelFile.exists() || modelFile.length() < 10_000_000L) {
                setStatus("Extracting model...");
                copyAssetToFile(MODEL_ASSET, modelFile);
            }
            setStatus("Loading model into memory...");
            boolean ok = LlamaModel.nativeLoad(modelFile.getAbsolutePath(), N_CTX, N_THREADS);
            if (ok && LlamaModel.nativeIsLoaded()) {
                modelLoaded.set(true);
                setStatus("Ready. Say hello!");
                handler.post(() -> {
                    sendButton.setEnabled(true);
                    input.setEnabled(true);
                    input.requestFocus();
                });
            } else {
                setStatus("Model load failed");
                showToast("Failed to load model");
            }
        } catch (Throwable t) {
            setStatus("Error: " + t.getMessage());
            showToast("Load error: " + t.getMessage());
        }
    }

    private void copyAssetToFile(String assetPath, File dest) throws Exception {
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            long copied = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                copied += n;
            }
            out.flush();
        }
    }

    private void sendMessage() {
        if (!modelLoaded.get()) {
            showToast("Model not ready yet");
            return;
        }
        if (generating.get()) {
            showToast("Still generating...");
            return;
        }
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }
        input.setText("");

        adapter.add(new ChatMessage(ChatMessage.ROLE_USER, text));

        // 构建包含历史上下文的 prompt
        List<ChatMessage> history = new ArrayList<>(adapter.getAll());
        history.remove(history.size() - 1); // 去掉刚加入的 user 消息，buildPrompt 会单独处理
        String prompt = buildPrompt(history, text);

        ChatMessage assistantMsg = new ChatMessage(ChatMessage.ROLE_ASSISTANT, "");
        assistantMsg.streaming = true;
        adapter.add(assistantMsg);

        generating.set(true);
        setStatus("Generating...");
        handler.post(() -> {
            sendButton.setEnabled(false);
            stopButton.setEnabled(true);
        });

        executor.execute(() -> {
            try {
                LlamaModel.nativeGenerate(prompt, MAX_TOKENS, MainActivity.this);
            } catch (Throwable t) {
                onError("Generation error: " + t.getMessage());
            }
        });
    }

    /** 用 ChatML 模板构建带上下文的 prompt（SmolLM2 使用 im_start/im_end）。 */
    private String buildPrompt(List<ChatMessage> history, String userText) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<|im_start|>system\n").append(SYSTEM_PROMPT).append("<|im_end|>\n");

        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String role = m.role == ChatMessage.ROLE_USER ? "user" : "assistant";
            sb.append("<|im_start|>").append(role).append("\n")
              .append(truncate(m.content)).append("<|im_end|>\n");
        }
        sb.append("<|im_start|>user\n").append(truncate(userText)).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");
        return sb.toString();
    }

    private String truncate(String s) {
        if (s.length() <= 400) {
            return s;
        }
        return s.substring(0, 400);
    }

    private void stopGeneration() {
        if (generating.get()) {
            LlamaModel.nativeStop();
        }
    }

    // ---------- LlamaModel.Listener 回调（工作线程触发） ----------

    @Override
    public void onToken(String token) {
        handler.post(() -> {
            pending.append(token);
            long now = SystemClock.uptimeMillis();
            if (now - lastRefresh >= 200) {
                flushPending();
            }
        });
    }

    @Override
    public void onDone(String fullText) {
        handler.post(() -> {
            flushPending();
            generating.set(false);
            setStatus("Ready");
            sendButton.setEnabled(true);
            stopButton.setEnabled(false);
            input.requestFocus();
        });
    }

    @Override
    public void onError(String message) {
        handler.post(() -> {
            flushPending();
            generating.set(false);
            setStatus("Ready");
            sendButton.setEnabled(true);
            stopButton.setEnabled(false);
            showToast(message);
        });
    }

    private void flushPending() {
        if (pending.length() == 0) {
            return;
        }
        ChatMessage last = adapter.getLast();
        if (last != null) {
            last.content += pending.toString();
            last.streaming = false;
        }
        pending.setLength(0);
        adapter.notifyChanged();
        listView.setSelection(adapter.getCount() - 1);
        lastRefresh = SystemClock.uptimeMillis();
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (generating.get()) {
            LlamaModel.nativeStop();
        }
        LlamaModel.nativeUnload();
        executor.shutdownNow();
    }
}
