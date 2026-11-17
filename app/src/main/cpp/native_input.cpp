#include <jni.h>
#include <linux/input.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <poll.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace {
struct Device { int fd=-1; std::string path; };
struct Session {
    JavaVM* vm=nullptr; jobject callback=nullptr; jmethodID onBatch=nullptr; jmethodID onEnded=nullptr;
    int stopFd=-1; std::vector<Device> devices; std::thread worker;
    std::atomic<bool> stop{false}; std::atomic<bool> running{false};
};
std::mutex gMutex;
std::shared_ptr<Session> gSession;

void releaseDevices(const std::shared_ptr<Session>& s) {
    for(auto& d:s->devices) if(d.fd>=0) { ioctl(d.fd,EVIOCGRAB,0); close(d.fd); d.fd=-1; }
    if(s->stopFd>=0) { close(s->stopFd); s->stopFd=-1; }
}
bool flush(JNIEnv* env,const std::shared_ptr<Session>& s,std::vector<jint>& batch) {
    if(batch.empty()) return true;
    jintArray a=env->NewIntArray(static_cast<jsize>(batch.size()));
    if(!a) return false;
    env->SetIntArrayRegion(a,0,static_cast<jsize>(batch.size()),batch.data());
    if(env->ExceptionCheck()) { env->ExceptionClear(); env->DeleteLocalRef(a); return false; }
    env->CallVoidMethod(s->callback,s->onBatch,a); env->DeleteLocalRef(a); batch.clear();
    if(env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    return true;
}
void worker(const std::shared_ptr<Session>& s) {
    JNIEnv* env=nullptr; bool attached=false;
    if(s->vm->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6)!=JNI_OK) {
        if(s->vm->AttachCurrentThread(&env,nullptr)!=JNI_OK) { s->running=false; releaseDevices(s); return; }
        attached=true;
    }
    std::vector<jint> batch; batch.reserve(128);
    std::vector<pollfd> fds; fds.reserve(s->devices.size()+1);
    fds.push_back(pollfd{s->stopFd,POLLIN,0});
    for(const auto& d:s->devices) fds.push_back(pollfd{d.fd,static_cast<short>(POLLIN|POLLERR|POLLHUP),0});
    int error=0; bool callbackFailed=false;
    while(!s->stop.load()) {
        int ready=poll(fds.data(),fds.size(),16);
        if(ready<0) { if(errno==EINTR) continue; error=errno; break; }
        if(fds[0].revents&POLLIN) { eventfd_t value=0; eventfd_read(s->stopFd,&value); break; }
        for(size_t i=1;i<fds.size();++i) {
            short flags=fds[i].revents;
            if(flags&(POLLERR|POLLHUP|POLLNVAL)) { error=ENODEV; break; }
            if(!(flags&POLLIN)) continue;
            input_event ev{};
            while(!s->stop.load()) {
                ssize_t n=read(fds[i].fd,&ev,sizeof(ev));
                if(n==static_cast<ssize_t>(sizeof(ev))) {
                    if(ev.type==EV_SYN||ev.type==EV_KEY||ev.type==EV_REL) {
                        batch.push_back(static_cast<jint>(i-1)); batch.push_back(ev.type);
                        batch.push_back(ev.code); batch.push_back(ev.value);
                        if(batch.size()>=128&&!flush(env,s,batch)) { callbackFailed=true; error=EPIPE; break; }
                    }
                    continue;
                }
                if(n<0&&errno==EINTR) continue;
                if(n<0&&(errno==EAGAIN||errno==EWOULDBLOCK)) break;
                error=n==0?EIO:errno; break;
            }
            if(error||callbackFailed) break;
        }
        if(error||callbackFailed) break;
        if(!flush(env,s,batch)) { callbackFailed=true; error=EPIPE; break; }
    }
    if(!s->stop.load()&&error&&!callbackFailed) {
        env->CallVoidMethod(s->callback,s->onEnded,static_cast<jint>(error));
        if(env->ExceptionCheck()) env->ExceptionClear();
    }
    releaseDevices(s); s->running=false;
    if(s->callback) { env->DeleteGlobalRef(s->callback); s->callback=nullptr; }
    if(attached) s->vm->DetachCurrentThread();
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_apx_mouse_NativeEvdevCapture_startNative(JNIEnv* env,jobject,jobjectArray paths,jobject callback) {
    if(!paths||!callback) return -EINVAL;
    jsize count=env->GetArrayLength(paths); if(count<1||count>8) return -EINVAL;
    std::shared_ptr<Session> stale;
    { std::lock_guard<std::mutex> lock(gMutex); if(gSession&&gSession->running) return -EBUSY; stale=gSession; gSession.reset(); }
    if(stale&&stale->worker.joinable()) stale->worker.join();
    auto s=std::make_shared<Session>();
    if(env->GetJavaVM(&s->vm)!=JNI_OK) return -EFAULT;
    jclass cls=env->GetObjectClass(callback); if(!cls) return -EFAULT;
    s->onBatch=env->GetMethodID(cls,"onNativeInputEventBatch","([I)V");
    s->onEnded=env->GetMethodID(cls,"onNativeInputCaptureEnded","(I)V");
    env->DeleteLocalRef(cls);
    if(!s->onBatch||!s->onEnded||env->ExceptionCheck()) { if(env->ExceptionCheck()) env->ExceptionClear(); return -ENOSYS; }
    s->callback=env->NewGlobalRef(callback); if(!s->callback) return -ENOMEM;
    for(jsize i=0;i<count;++i) {
        auto obj=static_cast<jstring>(env->GetObjectArrayElement(paths,i));
        if(!obj) { releaseDevices(s); env->DeleteGlobalRef(s->callback); return -EINVAL; }
        const char* chars=env->GetStringUTFChars(obj,nullptr);
        if(!chars) { env->DeleteLocalRef(obj); releaseDevices(s); env->DeleteGlobalRef(s->callback); return -ENOMEM; }
        std::string path(chars); env->ReleaseStringUTFChars(obj,chars); env->DeleteLocalRef(obj);
        if(path.rfind("/dev/input/event",0)!=0||path.size()<=16||path.find_first_not_of("0123456789",16)!=std::string::npos) {
            releaseDevices(s); env->DeleteGlobalRef(s->callback); return -EINVAL;
        }
        int fd=open(path.c_str(),O_RDONLY|O_NONBLOCK|O_CLOEXEC);
        if(fd<0) { int e=errno; releaseDevices(s); env->DeleteGlobalRef(s->callback); return -e; }
        if(ioctl(fd,EVIOCGRAB,1)<0) { int e=errno; close(fd); releaseDevices(s); env->DeleteGlobalRef(s->callback); return -e; }
        s->devices.push_back(Device{fd,path});
    }
    s->stopFd=eventfd(0,EFD_CLOEXEC|EFD_NONBLOCK);
    if(s->stopFd<0) { int e=errno; releaseDevices(s); env->DeleteGlobalRef(s->callback); return -e; }
    s->running=true;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if(gSession) { s->running=false; releaseDevices(s); env->DeleteGlobalRef(s->callback); return -EBUSY; }
        gSession=s;
        try { s->worker=std::thread(worker,s); }
        catch(...) { gSession.reset(); s->running=false; releaseDevices(s); env->DeleteGlobalRef(s->callback); s->callback=nullptr; return -EAGAIN; }
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_apx_mouse_NativeEvdevCapture_stopNative(JNIEnv*,jobject) {
    std::shared_ptr<Session> s;
    { std::lock_guard<std::mutex> lock(gMutex); s=gSession; if(!s) return; s->stop=true; if(s->stopFd>=0) eventfd_write(s->stopFd,1); }
    if(s->worker.joinable()&&s->worker.get_id()!=std::this_thread::get_id()) s->worker.join();
    { std::lock_guard<std::mutex> lock(gMutex); if(gSession==s) gSession.reset(); }
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_apx_mouse_NativeEvdevCapture_isRunningNative(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> lock(gMutex); return gSession&&gSession->running?JNI_TRUE:JNI_FALSE;
}
