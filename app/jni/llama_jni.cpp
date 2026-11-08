// llama_jni.cpp —— llama.cpp 的 JNI 绑定
// 目标：在低内存 32 位 ARM 设备上运行 SmolLM2-135M 做英文对话。
#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <cstring>

#include "llama.h"

// ---------- 全局单例状态 ----------
static llama_model       * g_model   = nullptr;
static llama_context     * g_ctx     = nullptr;
static const llama_vocab * g_vocab   = nullptr;
static llama_sampler     * g_sampler = nullptr;
static std::atomic<bool>   g_stop{false};
static JavaVM * g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM * vm, void * /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ereader_chat_LlamaModel_nativeLoad(JNIEnv * env, jclass, jstring jpath,
                                            jint n_ctx, jint n_threads) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) return JNI_FALSE;

    // 模型参数：纯 CPU 推理 + mmap 按需加载（省内存）
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;                 // 不使用 GPU
    mparams.load_mode    = LLAMA_LOAD_MODE_MMAP; // mmap，权重按需换页，降低物理内存占用

    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (g_model == nullptr) return JNI_FALSE;

    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = n_ctx;
    cparams.n_threads        = n_threads;
    cparams.n_threads_batch  = n_threads;
    cparams.n_batch          = 64;   // 小 batch，降低峰值内存

    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // 采样链：top_p -> temperature -> distribution
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(1234));

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ereader_chat_LlamaModel_nativeIsLoaded(JNIEnv *, jclass) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ereader_chat_LlamaModel_nativeGenerate(JNIEnv * env, jclass,
                                                jstring jprompt, jint max_tokens,
                                                jobject listener) {
    if (g_model == nullptr || g_ctx == nullptr || g_sampler == nullptr) {
        return;
    }

    g_stop.store(false);

    // 缓存 Java 回调方法
    jclass cls = env->GetObjectClass(listener);
    jmethodID m_onToken = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    jmethodID m_onDone  = env->GetMethodID(cls, "onDone",  "(Ljava/lang/String;)V");
    jmethodID m_onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);

    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string full;

    // 1. 分词
    const int n_prompt = (int) strlen(prompt);
    std::vector<llama_token> tokens(n_prompt + 16);
    int n = llama_tokenize(g_vocab, prompt, n_prompt, tokens.data(),
                           (int) tokens.size(), true, true);
    env->ReleaseStringUTFChars(jprompt, prompt);
    if (n < 0) {
        jstring jmsg = env->NewStringUTF("tokenize failed");
        env->CallVoidMethod(listener, m_onError, jmsg);
        env->DeleteLocalRef(jmsg);
        return;
    }
    tokens.resize(n);

    // 2. 预填充（首轮 decode 整个 prompt）
    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    int rc = llama_decode(g_ctx, batch);
    if (rc != 0) {
        jstring jmsg = env->NewStringUTF("decode failed");
        env->CallVoidMethod(listener, m_onError, jmsg);
        env->DeleteLocalRef(jmsg);
        return;
    }

    // 3. 逐 token 生成并回调
    for (int i = 0; i < max_tokens; i++) {
        if (g_stop.load()) {
            break;
        }
        llama_token new_tok = llama_sampler_sample(g_sampler, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, new_tok)) {
            break;
        }
        const char * piece = llama_vocab_get_text(g_vocab, new_tok);
        if (piece != nullptr) {
            full += piece;
            jstring jpiece = env->NewStringUTF(piece);
            env->CallVoidMethod(listener, m_onToken, jpiece);
            env->DeleteLocalRef(jpiece);
        }
        // 单 token 增量 decode
        llama_batch b1 = llama_batch_get_one(&new_tok, 1);
        if (llama_decode(g_ctx, b1) != 0) {
            break;
        }
    }

    // 4. 结束回调
    if (g_stop.load()) {
        jstring jmsg = env->NewStringUTF("stopped");
        env->CallVoidMethod(listener, m_onError, jmsg);
        env->DeleteLocalRef(jmsg);
    } else {
        jstring jfull = env->NewStringUTF(full.c_str());
        env->CallVoidMethod(listener, m_onDone, jfull);
        env->DeleteLocalRef(jfull);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ereader_chat_LlamaModel_nativeStop(JNIEnv *, jclass) {
    g_stop.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ereader_chat_LlamaModel_nativeUnload(JNIEnv *, jclass) {
    g_stop.store(true);
    if (g_sampler != nullptr) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx     != nullptr) { llama_free(g_ctx);            g_ctx     = nullptr; }
    if (g_model   != nullptr) { llama_model_free(g_model);    g_model   = nullptr; }
    g_vocab = nullptr;
}
