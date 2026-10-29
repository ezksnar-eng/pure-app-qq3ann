#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <algorithm>
#include <string>
#include <vector>
#include "llama.h"

#define TAG "Vera"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static std::atomic<bool> g_stop{false};
static bool g_inited = false;

// أطول جزء بداية من النص صالح UTF-8 (حتى ما تنقطع الحروف العربية)
static size_t utf8_valid_prefix(const std::string &s) {
    size_t i = 0, n = s.size();
    while (i < n) {
        unsigned char c = (unsigned char) s[i];
        size_t len = c < 0x80 ? 1 : (c >> 5) == 6 ? 2 : (c >> 4) == 14 ? 3 : (c >> 3) == 30 ? 4 : 0;
        if (len == 0) { i++; continue; }
        if (i + len > n) break;
        i += len;
    }
    return i;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vera_app_Native_load(JNIEnv *env, jobject, jstring jpath) {
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (!g_inited) { llama_backend_init(); g_inited = true; }
    const char *c = env->GetStringUTFChars(jpath, nullptr);
    std::string path(c);
    env->ReleaseStringUTFChars(jpath, c);

    llama_model_params mp = llama_model_default_params();
    mp.use_mmap = true;
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return env->NewStringUTF("ERR_LOAD");
    LOGI("model loaded");
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vera_app_Native_generate(JNIEnv *env, jobject, jbyteArray jprompt, jint maxTokens,
                                  jfloat temp, jfloat topP, jint nCtx, jint threads, jobject cb) {
    if (!g_model) return env->NewStringUTF("ERR_NOMODEL");
    g_stop = false;

    jsize plen = env->GetArrayLength(jprompt);
    std::string prompt((size_t) plen, '\0');
    env->GetByteArrayRegion(jprompt, 0, plen, reinterpret_cast<jbyte *>(&prompt[0]));

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    int n = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    if (n <= 0) return env->NewStringUTF("ERR_TOKENIZE");
    std::vector<llama_token> toks((size_t) n);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), n, true, true) < 0)
        return env->NewStringUTF("ERR_TOKENIZE");
    if (n >= nCtx - 16) return env->NewStringUTF("ERR_TOO_LONG");
    int budget = std::min<int>(maxTokens, nCtx - n);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_batch = 512;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context *ctx = llama_init_from_model(g_model, cp);
    if (!ctx) return env->NewStringUTF("ERR_CTX");

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    if (temp <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    auto cleanup = [&]() { llama_sampler_free(smpl); llama_free(ctx); };

    // قراءة البرومبت على دفعات
    size_t pos = 0;
    while (pos < toks.size()) {
        if (g_stop) { cleanup(); return env->NewStringUTF(""); }
        int k = (int) std::min<size_t>(512, toks.size() - pos);
        llama_batch b = llama_batch_get_one(toks.data() + pos, k);
        if (llama_decode(ctx, b) != 0) { cleanup(); return env->NewStringUTF("ERR_DECODE"); }
        pos += (size_t) k;
    }

    jclass cls = env->GetObjectClass(cb);
    jmethodID mid = env->GetMethodID(cls, "onToken", "([B)V");
    auto emit = [&](const std::string &s) {
        jbyteArray arr = env->NewByteArray((jsize) s.size());
        env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
        env->CallVoidMethod(cb, mid, arr);
        env->DeleteLocalRef(arr);
    };

    std::string pending;
    for (int i = 0; i < budget && !g_stop; i++) {
        llama_token tok = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            pending.append(buf, (size_t) len);
            size_t ok = utf8_valid_prefix(pending);
            if (ok > 0) { emit(pending.substr(0, ok)); pending.erase(0, ok); }
        }
        llama_batch b = llama_batch_get_one(&tok, 1);
        if (llama_decode(ctx, b) != 0) break;
    }
    if (!pending.empty()) emit(pending);

    cleanup();
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_vera_app_Native_stop(JNIEnv *, jobject) { g_stop = true; }

extern "C" JNIEXPORT void JNICALL
Java_com_vera_app_Native_unload(JNIEnv *, jobject) {
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}
