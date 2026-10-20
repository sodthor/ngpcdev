#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <z80ex/z80ex.h>

typedef struct {
    Z80EX_CONTEXT *cpu;
    unsigned char *shared;
    long psg, audible, irq, nmi, tlcs_irq, invalid;
    int balance, timer, pending_nmi;
} Sound;

static Z80EX_BYTE memory_read(Z80EX_CONTEXT *cpu, Z80EX_WORD address, int fetch, void *data) {
    Sound *sound = data;
    if (address < 0x4000) return sound->shared[address & 0xfff];
    if (address == 0x8000) return sound->shared[0x10bc];
    if (fetch) sound->invalid++;
    return 0xff;
}

static void memory_write(Z80EX_CONTEXT *cpu, Z80EX_WORD address, Z80EX_BYTE value, void *data) {
    Sound *sound = data;
    if (address < 0x4000) sound->shared[address & 0xfff] = value;
    else if (address == 0x4000 || address == 0x4001) {
        sound->psg++;
        if ((value & 0x90) == 0x90 && (value & 15) != 15) sound->audible++;
    } else if (address == 0x8000) sound->shared[0x10bc] = value;
    else if (address == 0xc000) sound->tlcs_irq++;
    else sound->invalid++;
}

static Z80EX_BYTE port_read(Z80EX_CONTEXT *cpu, Z80EX_WORD port, void *data) { return 0xff; }
static void port_write(Z80EX_CONTEXT *cpu, Z80EX_WORD port, Z80EX_BYTE value, void *data) {}
static Z80EX_BYTE interrupt_read(Z80EX_CONTEXT *cpu, void *data) { return 0xff; }

JNIEXPORT jlong JNICALL Java_sim_tests_Z80SoundDevice_create(JNIEnv *env, jclass type, jobject buffer) {
    Sound *sound = calloc(1, sizeof(*sound));
    if (!sound) return 0;
    sound->shared = (*env)->GetDirectBufferAddress(env, buffer);
    sound->cpu = z80ex_create(memory_read, sound, memory_write, sound, port_read, sound,
        port_write, sound, interrupt_read, sound);
    return (jlong)(intptr_t)sound;
}

JNIEXPORT void JNICALL Java_sim_tests_Z80SoundDevice_reset(JNIEnv *env, jclass type, jlong context) {
    Sound *sound = (Sound *)(intptr_t)context;
    z80ex_reset(sound->cpu);
    sound->balance = sound->timer = sound->pending_nmi = 0;
}

JNIEXPORT void JNICALL Java_sim_tests_Z80SoundDevice_advance(JNIEnv *env, jclass type,
        jlong context, jint cycles, jint period, jboolean nmi) {
    Sound *sound = (Sound *)(intptr_t)context;
    sound->balance += cycles;
    if (nmi) sound->pending_nmi = 1;
    while (sound->balance > 0) {
        int elapsed = 0;
        if (sound->pending_nmi && z80ex_nmi_possible(sound->cpu)) {
            elapsed = z80ex_nmi(sound->cpu);
            if (elapsed) { sound->pending_nmi = 0; sound->nmi++; }
        }
        if (!elapsed && period && sound->timer >= period && z80ex_int_possible(sound->cpu)) {
            elapsed = z80ex_int(sound->cpu);
            if (elapsed) { sound->timer %= period; sound->irq++; }
        }
        if (!elapsed) elapsed = z80ex_step(sound->cpu);
        sound->balance -= elapsed;
        if (period) sound->timer += elapsed;
        else sound->timer = 0;
    }
}

JNIEXPORT jlongArray JNICALL Java_sim_tests_Z80SoundDevice_state(JNIEnv *env, jclass type, jlong context) {
    Sound *sound = (Sound *)(intptr_t)context;
    jlong values[] = {z80ex_get_reg(sound->cpu, regPC), z80ex_get_reg(sound->cpu, regSP),
        sound->psg, sound->audible, sound->irq, sound->nmi, sound->tlcs_irq, sound->invalid};
    jlongArray result = (*env)->NewLongArray(env, 8);
    (*env)->SetLongArrayRegion(env, result, 0, 8, values);
    return result;
}

JNIEXPORT void JNICALL Java_sim_tests_Z80SoundDevice_destroy(JNIEnv *env, jclass type, jlong context) {
    Sound *sound = (Sound *)(intptr_t)context;
    z80ex_destroy(sound->cpu);
    free(sound);
}