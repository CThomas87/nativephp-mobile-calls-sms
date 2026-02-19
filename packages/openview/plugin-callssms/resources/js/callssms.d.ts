/**
 * @openview/plugin-callssms
 *
 * NativePHP Mobile plugin for native phone calls, SMS, WhatsApp integration,
 * and contact picker on iOS and Android.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Parameter types
// ─────────────────────────────────────────────────────────────────────────────

export interface PhoneDialerOptions {
    /**
     * When true, attempt to place the call immediately (requires CALL_PHONE
     * permission on Android; uses `tel://` URL scheme on iOS).
     * When false (default), opens the dialler pre-filled without calling.
     */
    autoCall?: boolean;
}

export interface SmsOptions {
    /**
     * Pre-filled message body shown in the SMS composer.
     */
    message?: string;
}

export interface WhatsappOptions {
    /**
     * Pre-filled text (message mode only).
     */
    message?: string;
    /**
     * 'message' (default) — open WhatsApp chat with optional pre-filled text.
     * 'call'    — open WhatsApp and navigate to the contact for a voice call.
     */
    mode?: 'message' | 'call';
}

export interface ContactPickerOptions {
    /**
     * 'device'   (default) — use the system contacts picker.
     * 'whatsapp' — use the WhatsApp contacts picker on Android;
     *              falls back to system picker on iOS.
     */
    source?: 'device' | 'whatsapp';
}

// ─────────────────────────────────────────────────────────────────────────────
// Return types
// ─────────────────────────────────────────────────────────────────────────────

export interface BridgeSuccessResponse {
    success: boolean;
}

export interface PickContactResponse {
    success: boolean;
    message: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Event payload
// ─────────────────────────────────────────────────────────────────────────────

export interface ContactSelectedPayload {
    /** Display name of the selected contact. */
    name: string;
    /** Phone number of the selected contact. */
    phone: string;
    /** Source that provided the contact: 'device' or 'whatsapp'. */
    source: 'device' | 'whatsapp';
}

// ─────────────────────────────────────────────────────────────────────────────
// Bridge functions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Open the device's native phone dialler pre-filled with `phone`.
 *
 * @param phone     Recipient phone number (any format accepted by the dialler).
 * @param options   Optional settings; see PhoneDialerOptions.
 */
export declare function openPhoneDialer(
    phone: string,
    options?: PhoneDialerOptions
): Promise<BridgeSuccessResponse>;

/**
 * Open the device's native SMS / MMS composer pre-filled with `phone`.
 *
 * @param phone     Recipient phone number.
 * @param options   Optional settings; see SmsOptions.
 */
export declare function openSms(
    phone: string,
    options?: SmsOptions
): Promise<BridgeSuccessResponse>;

/**
 * Open WhatsApp for `phone` in message or call mode.
 *
 * Falls back to the wa.me web URL when WhatsApp is not installed.
 *
 * @param phone     Phone number in international format (+country-code).
 * @param options   Optional settings; see WhatsappOptions.
 */
export declare function openWhatsapp(
    phone: string,
    options?: WhatsappOptions
): Promise<BridgeSuccessResponse>;

/**
 * Launch the contact picker.
 *
 * The selected contact is returned asynchronously via the `ContactSelected`
 * PHP event. Subscribe with `OnContactSelected()` to receive it.
 *
 * @param options   Optional settings; see ContactPickerOptions.
 */
export declare function pickContact(
    options?: ContactPickerOptions
): Promise<PickContactResponse>;

// ─────────────────────────────────────────────────────────────────────────────
// Event constants
// ─────────────────────────────────────────────────────────────────────────────

export declare const Events: {
    /** Fully-qualified PHP event class name for the ContactSelected event. */
    readonly ContactSelected: string;
};

// ─────────────────────────────────────────────────────────────────────────────
// Event listener helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Subscribe to the ContactSelected event.
 *
 * @param callback  Receives `ContactSelectedPayload` when a contact is picked.
 *
 * @example
 *   import { OnContactSelected } from '@openview/plugin-callssms';
 *
 *   OnContactSelected(({ name, phone, source }) => {
 *       console.log(`Picked ${name} – ${phone} (from ${source})`);
 *   });
 */
export declare function OnContactSelected(
    callback: (data: ContactSelectedPayload) => void
): void;

/**
 * Unsubscribe from the ContactSelected event.
 *
 * @param callback  The same function reference passed to OnContactSelected.
 */
export declare function OffContactSelected(
    callback: (data: ContactSelectedPayload) => void
): void;

// ─────────────────────────────────────────────────────────────────────────────
// Namespace export
// ─────────────────────────────────────────────────────────────────────────────

export declare const CallsSms: {
    openPhoneDialer: typeof openPhoneDialer;
    openSms: typeof openSms;
    openWhatsapp: typeof openWhatsapp;
    pickContact: typeof pickContact;
    OnContactSelected: typeof OnContactSelected;
    OffContactSelected: typeof OffContactSelected;
};

export default CallsSms;
