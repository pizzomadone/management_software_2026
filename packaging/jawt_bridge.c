#include <jawt.h>
#include <jawt_md.h>

extern JAWT_DrawingSurface* JNICALL awt_GetDrawingSurface(JNIEnv*, jobject);
extern void JNICALL awt_FreeDrawingSurface(JAWT_DrawingSurface*);
extern void JNICALL awt_Lock(JNIEnv*);
extern void JNICALL awt_Unlock(JNIEnv*);
extern jobject JNICALL awt_GetComponent(JNIEnv*, void*);
extern jobject JNICALL awt_CreateEmbeddedFrame(JNIEnv*, void*);
extern void JNICALL awt_SetBounds(JNIEnv*, jobject, jint, jint, jint, jint);
extern void JNICALL awt_SynthesizeWindowActivation(JNIEnv*, jobject, jboolean);

/*
 * Packaging-only JAWT entry point for Native Image on macOS.
 * The actual drawing-surface implementation remains the JDK/NIK AWT code.
 */
JNIEXPORT jboolean JNICALL JAWT_GetAWT(JNIEnv* env, JAWT* awt) {
    (void) env;
    if (awt == NULL) {
        return JNI_FALSE;
    }

    if (awt->version != (JAWT_VERSION_1_4 | JAWT_MACOSX_USE_CALAYER) &&
        awt->version != JAWT_VERSION_1_7 &&
        awt->version != JAWT_VERSION_9) {
        return JNI_FALSE;
    }

    awt->GetDrawingSurface = awt_GetDrawingSurface;
    awt->FreeDrawingSurface = awt_FreeDrawingSurface;
    awt->Lock = awt_Lock;
    awt->Unlock = awt_Unlock;
    awt->GetComponent = awt_GetComponent;

    if (awt->version >= JAWT_VERSION_9) {
        awt->CreateEmbeddedFrame = awt_CreateEmbeddedFrame;
        awt->SetBounds = awt_SetBounds;
        awt->SynthesizeWindowActivation = awt_SynthesizeWindowActivation;
    }

    return JNI_TRUE;
}
