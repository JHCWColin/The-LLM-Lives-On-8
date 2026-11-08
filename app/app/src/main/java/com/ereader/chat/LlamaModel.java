package com.ereader.chat;

/**
 * llama.cpp 的 JNI 封装。
 * 所有 native 方法都操作 native 侧的全局单例模型上下文（本设备内存只够同时驻留一个模型）。
 */
public final class LlamaModel {

    static {
        System.loadLibrary("llama");
    }

    /** 生成过程中的回调。所有回调都从 native 生成线程触发。 */
    public interface Listener {
        /** 每产生一段文本（一个或多个 token）回调一次，参数为增量文本。 */
        void onToken(String token);

        /** 生成正常结束，参数为本次完整回复文本。 */
        void onDone(String fullText);

        /** 发生错误或被停止。 */
        void onError(String message);
    }

    private LlamaModel() {
    }

    /** 加载模型。modelPath 为设备本地文件路径。返回是否成功。 */
    public static native boolean nativeLoad(String modelPath, int nCtx, int nThreads);

    /** 是否已加载。 */
    public static native boolean nativeIsLoaded();

    /**
     * 阻塞式生成。必须在工作线程调用。生成期间会回调 listener。
     * prompt 需要是已经格式化好的完整 prompt（含 chat 模板）。
     */
    public static native void nativeGenerate(String prompt, int maxTokens, Listener listener);

    /** 请求停止当前生成（供其他线程调用）。 */
    public static native void nativeStop();

    /** 卸载模型并释放内存。 */
    public static native void nativeUnload();
}
