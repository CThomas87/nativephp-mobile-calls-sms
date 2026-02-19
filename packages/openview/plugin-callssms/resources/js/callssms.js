/**
 * CallsSms Plugin for NativePHP Mobile – JavaScript interface
 *
 * Provides phone dialler, SMS, WhatsApp, and contact-picker bindings for
 * Vue / React / Inertia applications running inside NativePHP Mobile.
 *
 * @example
 *   import { CallsSms, OnContactSelected, Events } from '@openview/plugin-callssms';
 *
 *   // Open native phone dialler
 *   await CallsSms.openPhoneDialer('+15551234567');
 *
 *   // Auto-call (requires CALL_PHONE permission on Android)
 *   await CallsSms.openPhoneDialer('+15551234567', { autoCall: true });
 *
 *   // Send an SMS with pre-filled message
 *   await CallsSms.openSms('+15551234567', { message: 'Hello!' });
 *
 *   // Open WhatsApp chat
 *   await CallsSms.openWhatsapp('+15551234567', { message: 'Hey', mode: 'message' });
 *
 *   // Open WhatsApp call screen
 *   await CallsSms.openWhatsapp('+15551234567', { mode: 'call' });
 *
 *   // Pick a contact (result dispatched as PHP ContactSelected event)
 *   await CallsSms.pickContact();                       // device contacts
 *   await CallsSms.pickContact({ source: 'whatsapp' }); // WhatsApp contacts
 *
 *   // Listen for the contact-selected event
 *   OnContactSelected(({ name, phone, source }) => {
 *       console.log(`Selected: ${name} – ${phone} (${source})`);
 *   });
 */

import { On, Off } from '@nativephp/mobile';

const BASE_URL = '/_native/api/call';

// ─────────────────────────────────────────────────────────────────────────────
// Internal bridge helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Post a method + params to the NativePHP bridge endpoint.
 * @private
 */
async function bridgeCall(method, params = {}) {
    const csrfToken =
        document.querySelector('meta[name="csrf-token"]')?.content ?? '';

    const response = await fetch(BASE_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': csrfToken,
        },
        body: JSON.stringify({ method, params }),
    });

    const result = await response.json();

    if (result.status === 'error') {
        throw new Error(result.message ?? 'Native bridge call failed');
    }

    // Unwrap double-nested data if present
    const data = result.data;
    return data?.data !== undefined ? data.data : data;
}

// ─────────────────────────────────────────────────────────────────────────────
// openPhoneDialer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Open the device's native phone dialler pre-filled with `phone`.
 *
 * @param  {string}  phone                 Recipient phone number (any format).
 * @param  {Object}  [options={}]
 * @param  {boolean} [options.autoCall]    true → attempt to dial immediately
 *                                         (requires CALL_PHONE on Android;
 *                                          uses `tel://` on iOS).
 *                                         false (default) → open dialler only
 *                                         (`telprompt://` on iOS).
 * @returns {Promise<{ success: boolean }>}
 */
export async function openPhoneDialer(phone, options = {}) {
    const { autoCall = false } = options;
    return bridgeCall('Callssms.OpenPhoneDialer', { phone, autoCall });
}

// ─────────────────────────────────────────────────────────────────────────────
// openSms
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Open the device's native SMS / MMS app with `phone` and an optional body.
 *
 * On iOS this presents `MFMessageComposeViewController` (native compose sheet).
 * On Android this fires an `ACTION_SENDTO` intent to the default SMS app.
 *
 * @param  {string}  phone             Recipient phone number.
 * @param  {Object}  [options={}]
 * @param  {string}  [options.message] Pre-filled message body.
 * @returns {Promise<{ success: boolean }>}
 */
export async function openSms(phone, options = {}) {
    const { message = '' } = options;
    return bridgeCall('Callssms.OpenSms', { phone, message });
}

// ─────────────────────────────────────────────────────────────────────────────
// openWhatsapp
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Open WhatsApp for `phone` in message or call mode.
 *
 * Message mode opens the chat pre-filled with `message`.
 * Call mode attempts to open the WhatsApp call screen (`whatsapp://call` deep
 * link – widely supported).  Falls back to the chat view if unavailable.
 *
 * @param  {string}  phone              Phone in international format (+country-code).
 * @param  {Object}  [options={}]
 * @param  {string}  [options.message]  Pre-filled text (message mode only).
 * @param  {string}  [options.mode]     'message' (default) | 'call'
 * @returns {Promise<{ success: boolean }>}
 */
export async function openWhatsapp(phone, options = {}) {
    const { message = '', mode = 'message' } = options;
    return bridgeCall('Callssms.OpenWhatsapp', { phone, message, mode });
}

// ─────────────────────────────────────────────────────────────────────────────
// pickContact
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Launch the contact picker.  The result is returned asynchronously via the
 * `ContactSelected` PHP event dispatched to your Livewire component.
 *
 * @param  {Object}  [options={}]
 * @param  {string}  [options.source]  'device' (default) – system contacts.
 *                                     'whatsapp' – WhatsApp contacts on Android;
 *                                                  system contacts on iOS.
 * @returns {Promise<{ success: boolean, message: string }>}
 */
export async function pickContact(options = {}) {
    const { source = 'device' } = options;
    return bridgeCall('Callssms.PickContact', { source });
}

// ─────────────────────────────────────────────────────────────────────────────
// Event name constants
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Event name constants mirroring the PHP event class names.
 * Pass to NativePHP's `On` / `Off` helpers when you need raw access.
 */
export const Events = {
    /** Fired when the user picks a contact via pickContact(). */
    ContactSelected: 'Openview\\Callssms\\Events\\ContactSelected',
};

// ─────────────────────────────────────────────────────────────────────────────
// Event listener helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Subscribe to the ContactSelected event.
 * The callback receives `{ name, phone, source }` when the user picks a contact.
 *
 * @param  {function({ name: string, phone: string, source: string }): void} callback
 * @returns {void}
 *
 * @example
 *   import { OnContactSelected } from '@openview/plugin-callssms';
 *
 *   OnContactSelected(({ name, phone, source }) => {
 *       console.log(`Selected: ${name} – ${phone} (${source})`);
 *   });
 */
export function OnContactSelected(callback) {
    return On(Events.ContactSelected, callback);
}

/**
 * Unsubscribe from the ContactSelected event.
 *
 * @param  {function} callback  The same function reference passed to OnContactSelected.
 * @returns {void}
 *
 * @example
 *   import { OnContactSelected, OffContactSelected } from '@openview/plugin-callssms';
 *
 *   function handler(data) { ... }
 *   OnContactSelected(handler);
 *   // Later:
 *   OffContactSelected(handler);
 */
export function OffContactSelected(callback) {
    return Off(Events.ContactSelected, callback);
}

// ─────────────────────────────────────────────────────────────────────────────
// Namespace export
// ─────────────────────────────────────────────────────────────────────────────

export const CallsSms = {
    openPhoneDialer,
    openSms,
    openWhatsapp,
    pickContact,
    OnContactSelected,
    OffContactSelected,
};

export default CallsSms;
