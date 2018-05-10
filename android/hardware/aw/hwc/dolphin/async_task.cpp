

#include <utils/Thread.h>
#include "hwc.h"

class AsyncTask: public android::Thread {
    mutable android::Mutex mLock;
    mutable android::Condition mCondition;
    int mRequest;
    int mDelay;

public:
    const int WAITDELAY = 0x1;

    AsyncTask() : mRequest(0) {}
   ~AsyncTask() {}

    void wait3dOuputStable(int delay) {
        android::Mutex::Autolock _l(mLock);
        mDelay = delay;
        mRequest |= WAITDELAY;
        mCondition.signal();
        ALOGD("wait3dOuputStable: delay %dms", mDelay);
    }

private:
    virtual bool threadLoop() {
        android::Mutex::Autolock _l(mLock);
        mCondition.wait(mLock);

        if (mRequest & WAITDELAY) {
            mRequest &= (~WAITDELAY);
            usleep(mDelay * 1000);
            SUNXI_hwcdev_context_t *Globctx = &gSunxiHwcDevice;
            Globctx->outputMask = 0;
            Globctx->psHwcProcs->invalidate(Globctx->psHwcProcs);
            ALOGD("enable output");
        }
        return true;
    }
};

static android::sp<AsyncTask> asyncTask;

void initAsyncTask(void)
{
    asyncTask = new AsyncTask();
    asyncTask->run("async.task");
}

void wait3dOuputStableAsync(int delay)
{
    asyncTask->wait3dOuputStable(delay);
}

