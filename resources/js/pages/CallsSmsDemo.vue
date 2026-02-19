<script setup lang="ts">
/**
 * CallsSmsDemo.vue
 *
 * Demonstrates all features of the @openview/plugin-callssms NativePHP plugin:
 *  - Native phone dialler (device + WhatsApp)
 *  - SMS / messaging (device + WhatsApp)
 *  - Contact picker (device contacts + WhatsApp contacts)
 *
 * Inertia.js + Vue 3 Composition API + TailwindCSS v4
 */
import { Head } from '@inertiajs/vue3';
import { computed, onMounted, onUnmounted, ref } from 'vue';
import {
    OffContactSelected,
    OnContactSelected,
    openPhoneDialer,
    openSms,
    openWhatsapp,
    pickContact,
} from '@callssms';
import { BridgeCall } from '@nativephp/mobile';

// ─── Reactive state ──────────────────────────────────────────────────────────

/** Shared phone number field */
const phone = ref('');

/** Shared message field */
const message = ref('');

/** Default service: 'device' | 'whatsapp' */
const service = ref<'device' | 'whatsapp'>('device');

/** Whether to auto-call instead of just opening the dialler */
const autoCall = ref(false);

/** Loading / busy flag */
const busy = ref(false);

/** Stream of status messages shown in the log */
const log = ref<{ time: string; type: 'info' | 'success' | 'error'; text: string }[]>([]);

/** Last contact picked via the native contact picker */
const lastContact = ref<{ name: string; phone: string; source: string } | null>(null);

// ─── Computed helpers ────────────────────────────────────────────────────────

const isWhatsapp = computed(() => service.value === 'whatsapp');
const phoneClean = computed(() => phone.value.trim());

// ─── Log helper ──────────────────────────────────────────────────────────────

function addLog(type: 'info' | 'success' | 'error', text: string) {
    const now = new Date();
    const time = now.toLocaleTimeString();
    log.value.unshift({ time, type, text });
    if (log.value.length > 50) log.value.pop();
}

// ─── Actions ─────────────────────────────────────────────────────────────────

async function handleOpenDialler() {
    if (!phoneClean.value) return addLog('error', 'Please enter a phone number first.');

    busy.value = true;
    try {
        if (isWhatsapp.value) {
            addLog('info', `Opening WhatsApp call screen → ${phoneClean.value}`);
            await openWhatsapp(phoneClean.value, { mode: 'call' });
            addLog('success', 'WhatsApp call screen opened.');
        } else {
            const dial = autoCall.value;
            addLog('info', `Opening ${dial ? 'auto-call' : 'dialler'} → ${phoneClean.value}`);
            await openPhoneDialer(phoneClean.value, { autoCall: dial });
            addLog('success', `Dialler opened${dial ? ' (auto-call)' : ''}.`);
        }
    } catch (e: any) {
        addLog('error', `Dialler error: ${e?.message ?? e}`);
    } finally {
        busy.value = false;
    }
}

async function handleOpenSms() {
    if (!phoneClean.value) return addLog('error', 'Please enter a phone number first.');

    busy.value = true;
    try {
        if (isWhatsapp.value) {
            addLog('info', `Opening WhatsApp message → ${phoneClean.value}`);
            await openWhatsapp(phoneClean.value, { message: message.value, mode: 'message' });
            addLog('success', 'WhatsApp chat opened.');
        } else {
            addLog('info', `Opening SMS → ${phoneClean.value}`);
            await openSms(phoneClean.value, { message: message.value });
            addLog('success', 'SMS composer opened.');
        }
    } catch (e: any) {
        addLog('error', `Message error: ${e?.message ?? e}`);
    } finally {
        busy.value = false;
    }
}

async function handlePickContact() {
    busy.value = true;
    const src = service.value;
    addLog('info', `Opening ${src} contact picker…`);
    try {
        await pickContact({ source: src });
        addLog('info', 'Contact picker opened – awaiting selection.');
    } catch (e: any) {
        addLog('error', `Contact picker error: ${e?.message ?? e}`);
        busy.value = false;
    }
    // busy is cleared when the ContactSelected event fires (or after 30 s timeout)
    setTimeout(() => { busy.value = false; }, 30_000);
}

function fillFromContact() {
    if (lastContact.value) {
        phone.value = lastContact.value.phone;
        addLog('info', `Phone field filled from contact: ${lastContact.value.name}`);
    }
}

function clearLog() {
    log.value = [];
}

/** Index of the log entry that was just copied (for flash feedback) */
const copiedIndex = ref<number | null>(null);

async function copyLogEntry(index: number, text: string) {
    try {
        await navigator.clipboard.writeText(text);
    } catch {
        // Fallback for environments without clipboard API (e.g. some WebViews)
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
    }
    copiedIndex.value = index;
    setTimeout(() => {
        if (copiedIndex.value === index) copiedIndex.value = null;
    }, 1500);
}

// ─── NativePHP event subscriptions ───────────────────────────────────────────

function handleContactSelected(payload: { name: string; phone: string; source: string }) {
    lastContact.value = payload;
    busy.value = false;
    addLog('success', `Contact selected: ${payload.name} – ${payload.phone} (${payload.source})`);
}

onMounted(async () => {
    OnContactSelected(handleContactSelected);

    // Request all required permissions immediately on load.
    // On Android: shows system dialog for CALL_PHONE + READ_CONTACTS;
    //   if denied, a non-cancellable dialog offers to re-request or exit the app.
    // On iOS: requests CNContacts authorization;
    //   if denied, an alert directs the user to Settings or exits.
    try {
        await BridgeCall('Callssms.RequestPermissions', {});
        addLog('info', 'Permission request initiated.');
    } catch {
        addLog('info', 'Running outside NativePHP – permission check skipped.');
    }

    addLog('info', 'Demo page ready – listening for ContactSelected events.');
});

onUnmounted(() => {
    OffContactSelected(handleContactSelected);
});
</script>

<template>
    <Head title="Calls &amp; SMS Demo" />

    <div class="min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-950 dark:text-gray-100">

        <!-- ── Page header ─────────────────────────────────────────────── -->
        <header class="bg-white dark:bg-gray-900 shadow-sm px-4 py-5 flex items-center gap-3">
            <span class="text-2xl">📱</span>
            <div>
                <h1 class="text-xl font-bold leading-tight">Calls &amp; SMS Demo</h1>
                <p class="text-sm text-gray-500 dark:text-gray-400">NativePHP plugin – openview/plugin-callssms</p>
            </div>
        </header>

        <main class="max-w-2xl mx-auto px-4 py-6 space-y-6">

            <!-- ── Shared inputs ───────────────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-4">
                <h2 class="font-semibold text-base">Configuration</h2>

                <!-- Phone number -->
                <div class="space-y-1">
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
                        Phone number
                    </label>
                    <div class="flex gap-2">
                        <input
                            v-model="phone"
                            type="tel"
                            placeholder="+1 555 000 0000"
                            class="flex-1 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                        <button
                            v-if="lastContact"
                            @click="fillFromContact"
                            class="rounded-xl border border-gray-200 dark:border-gray-700 px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-800 transition"
                            title="Fill from last picked contact"
                        >
                            👤 Use contact
                        </button>
                    </div>
                </div>

                <!-- Message body -->
                <div class="space-y-1">
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
                        Message <span class="font-normal text-gray-400">(optional)</span>
                    </label>
                    <textarea
                        v-model="message"
                        rows="3"
                        placeholder="Pre-filled message text…"
                        class="w-full rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                    />
                </div>

                <!-- Service selector -->
                <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-gray-700 dark:text-gray-300">Service:</span>
                    <div class="flex rounded-xl overflow-hidden border border-gray-200 dark:border-gray-700 text-sm">
                        <button
                            @click="service = 'device'"
                            :class="[
                                'px-4 py-1.5 transition font-medium',
                                service === 'device'
                                    ? 'bg-indigo-600 text-white'
                                    : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                            ]"
                        >
                            📱 Device
                        </button>
                        <button
                            @click="service = 'whatsapp'"
                            :class="[
                                'px-4 py-1.5 transition font-medium',
                                service === 'whatsapp'
                                    ? 'bg-green-600 text-white'
                                    : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
                            ]"
                        >
                            💬 WhatsApp
                        </button>
                    </div>
                </div>
            </section>

            <!-- ── Phone call section ──────────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-3">
                <h2 class="font-semibold text-base flex items-center gap-2">
                    <span>📞</span>
                    <span>Phone Call</span>
                    <span
                        class="ml-auto text-xs px-2 py-0.5 rounded-full"
                        :class="isWhatsapp ? 'bg-green-100 text-green-700' : 'bg-indigo-100 text-indigo-700'"
                    >
                        {{ isWhatsapp ? 'WhatsApp' : 'Native dialler' }}
                    </span>
                </h2>

                <!-- Auto-call toggle (device only) -->
                <div v-if="!isWhatsapp" class="flex items-center gap-3">
                    <button
                        @click="autoCall = !autoCall"
                        :class="[
                            'relative inline-flex h-5 w-9 rounded-full transition-colors',
                            autoCall ? 'bg-indigo-600' : 'bg-gray-300 dark:bg-gray-600'
                        ]"
                        role="switch"
                        :aria-checked="autoCall"
                    >
                        <span
                            :class="['block h-4 w-4 rounded-full bg-white shadow m-0.5 transition-transform', autoCall ? 'translate-x-4' : 'translate-x-0']"
                        />
                    </button>
                    <span class="text-sm text-gray-600 dark:text-gray-400">
                        Auto-call immediately
                        <span class="text-xs text-gray-400">(requires CALL_PHONE permission on Android)</span>
                    </span>
                </div>

                <p v-if="isWhatsapp" class="text-sm text-gray-500 dark:text-gray-400">
                    Opens the WhatsApp conversation so you can start a call with one tap.
                </p>

                <button
                    @click="handleOpenDialler"
                    :disabled="busy || !phoneClean"
                    class="w-full rounded-xl py-2.5 px-4 font-semibold text-sm text-white transition disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2"
                    :class="isWhatsapp
                        ? 'bg-green-600 hover:bg-green-700 focus:ring-green-500'
                        : 'bg-indigo-600 hover:bg-indigo-700 focus:ring-indigo-500'"
                >
                    <span v-if="!busy">{{ isWhatsapp ? '💬 Open WhatsApp Call' : (autoCall ? '📲 Auto-call' : '📞 Open Dialler') }}</span>
                    <span v-else>⏳ Opening…</span>
                </button>
            </section>

            <!-- ── SMS / Message section ───────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-3">
                <h2 class="font-semibold text-base flex items-center gap-2">
                    <span>✉️</span>
                    <span>Send Message</span>
                    <span
                        class="ml-auto text-xs px-2 py-0.5 rounded-full"
                        :class="isWhatsapp ? 'bg-green-100 text-green-700' : 'bg-blue-100 text-blue-700'"
                    >
                        {{ isWhatsapp ? 'WhatsApp' : 'Native SMS' }}
                    </span>
                </h2>

                <p class="text-sm text-gray-500 dark:text-gray-400">
                    <template v-if="isWhatsapp">
                        Opens the WhatsApp chat pre-filled with your message.
                    </template>
                    <template v-else>
                        Opens the native SMS app (iOS: compose sheet; Android: default SMS app).
                    </template>
                </p>

                <button
                    @click="handleOpenSms"
                    :disabled="busy || !phoneClean"
                    class="w-full rounded-xl py-2.5 px-4 font-semibold text-sm text-white transition disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2"
                    :class="isWhatsapp
                        ? 'bg-green-600 hover:bg-green-700 focus:ring-green-500'
                        : 'bg-blue-600 hover:bg-blue-700 focus:ring-blue-500'"
                >
                    <span v-if="!busy">{{ isWhatsapp ? '💬 Open WhatsApp Chat' : '📤 Open SMS' }}</span>
                    <span v-else>⏳ Opening…</span>
                </button>
            </section>

            <!-- ── Contact picker section ──────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-3">
                <h2 class="font-semibold text-base flex items-center gap-2">
                    <span>👥</span>
                    <span>Contact Picker</span>
                    <span
                        class="ml-auto text-xs px-2 py-0.5 rounded-full"
                        :class="isWhatsapp ? 'bg-green-100 text-green-700' : 'bg-orange-100 text-orange-700'"
                    >
                        {{ isWhatsapp ? 'WhatsApp contacts (Android)' : 'Device contacts' }}
                    </span>
                </h2>

                <p class="text-sm text-gray-500 dark:text-gray-400">
                    Opens the {{ isWhatsapp ? 'WhatsApp' : 'system' }} contact picker. The selected contact
                    is returned via the <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">ContactSelected</code>
                    PHP event and shown below.
                    <template v-if="isWhatsapp">
                        On iOS the system contacts picker is used (WhatsApp does not expose a contact picker on iOS).
                    </template>
                </p>

                <button
                    @click="handlePickContact"
                    :disabled="busy"
                    class="w-full rounded-xl py-2.5 px-4 font-semibold text-sm text-white transition disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2"
                    :class="isWhatsapp
                        ? 'bg-green-600 hover:bg-green-700 focus:ring-green-500'
                        : 'bg-orange-500 hover:bg-orange-600 focus:ring-orange-400'"
                >
                    <span v-if="!busy">👤 Pick a Contact</span>
                    <span v-else>⏳ Waiting for contact…</span>
                </button>

                <!-- Last picked contact card -->
                <transition name="fade">
                    <div
                        v-if="lastContact"
                        class="mt-1 rounded-xl border border-gray-200 dark:border-gray-700 p-3 flex items-center gap-3"
                    >
                        <div class="h-10 w-10 rounded-full bg-indigo-100 dark:bg-indigo-900 flex items-center justify-center text-indigo-700 dark:text-indigo-300 font-bold text-lg shrink-0">
                            {{ lastContact.name.charAt(0).toUpperCase() }}
                        </div>
                        <div class="min-w-0 flex-1">
                            <p class="font-semibold text-sm truncate">{{ lastContact.name }}</p>
                            <p class="text-xs text-gray-500 dark:text-gray-400 truncate">{{ lastContact.phone }}</p>
                            <p class="text-xs text-gray-400 dark:text-gray-500">via {{ lastContact.source }}</p>
                        </div>
                        <button
                            @click="fillFromContact"
                            class="shrink-0 text-xs text-indigo-600 dark:text-indigo-400 hover:underline"
                        >
                            Use →
                        </button>
                    </div>
                </transition>
            </section>

            <!-- ── PHP Facade reference ────────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-3">
                <h2 class="font-semibold text-base flex items-center gap-2">
                    <span>🐘</span>
                    <span>PHP Facade Reference</span>
                </h2>
                <p class="text-sm text-gray-500 dark:text-gray-400">
                    The same actions are available from PHP via the <code class="text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">Callssms</code> facade:
                </p>
                <pre class="bg-gray-50 dark:bg-gray-800 rounded-xl p-4 text-xs overflow-x-auto text-gray-700 dark:text-gray-300"><code>use Openview\Callssms\Facades\Callssms;

// Open native dialler (optionally auto-call)
Callssms::openPhoneDialer('+15551234567');
Callssms::openPhoneDialer('+15551234567', autoCall: true);

// Open SMS composer
Callssms::openSms('+15551234567', message: 'Hello!');

// Open WhatsApp (message or call mode)
Callssms::openWhatsapp('+15551234567', message: 'Hey', mode: 'message');
Callssms::openWhatsapp('+15551234567', mode: 'call');

// Pick a contact (result fires ContactSelected event)
Callssms::pickContact();                       // device contacts
Callssms::pickContact(source: 'whatsapp');     // WhatsApp contacts

// Listen for the result
Event::listen(ContactSelected::class, function ($event) {
    $event->name;   // "Jane Doe"
    $event->phone;  // "+15551234567"
    $event->source; // "device" | "whatsapp"
});</code></pre>
            </section>

            <!-- ── Activity log ────────────────────────────────────────── -->
            <section class="bg-white dark:bg-gray-900 rounded-2xl shadow p-5 space-y-3">
                <div class="flex items-center justify-between">
                    <h2 class="font-semibold text-base">Activity Log</h2>
                    <button
                        v-if="log.length"
                        @click="clearLog"
                        class="text-xs text-gray-400 hover:text-red-500 dark:hover:text-red-400 transition"
                    >
                        Clear
                    </button>
                </div>
                <div v-if="!log.length" class="text-sm text-gray-400 dark:text-gray-600 text-center py-6">
                    No activity yet – try one of the actions above.
                </div>
                <ul v-else class="space-y-1 max-h-64 overflow-y-auto">
                    <li
                        v-for="(entry, i) in log"
                        :key="i"
                        @click="copyLogEntry(i, entry.text)"
                        class="group flex items-start gap-2 text-xs rounded-lg px-1.5 py-1 -mx-1.5 cursor-pointer transition"
                        :class="copiedIndex === i
                            ? 'bg-green-50 dark:bg-green-900/30'
                            : 'hover:bg-gray-50 dark:hover:bg-gray-800/60'"
                        title="Click to copy"
                    >
                        <span class="shrink-0 text-gray-400 tabular-nums w-16">{{ entry.time }}</span>
                        <span
                            class="shrink-0 font-medium"
                            :class="{
                                'text-gray-500  dark:text-gray-400': entry.type === 'info',
                                'text-green-600 dark:text-green-400': entry.type === 'success',
                                'text-red-600   dark:text-red-400':   entry.type === 'error',
                            }"
                        >{{ entry.type === 'info' ? 'ℹ' : entry.type === 'success' ? '✓' : '✗' }}</span>
                        <span class="flex-1 text-gray-700 dark:text-gray-300">{{ entry.text }}</span>
                        <span class="shrink-0 ml-1 transition-opacity"
                            :class="copiedIndex === i
                                ? 'opacity-100 text-green-600 dark:text-green-400'
                                : 'opacity-0 group-hover:opacity-60 text-gray-400'"
                        >{{ copiedIndex === i ? '✓ copied' : '⎘' }}</span>
                    </li>
                </ul>
            </section>

        </main>
    </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.fade-enter-from,
.fade-leave-to    { opacity: 0; transform: translateY(-4px); }
</style>
