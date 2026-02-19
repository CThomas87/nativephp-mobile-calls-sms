<?php

namespace Openview\Callssms;

/**
 * CallsSms plugin – provides native phone calls, SMS, and WhatsApp integration
 * for NativePHP Mobile on both iOS and Android.
 *
 * Service selector: 'device' uses the phone's native apps; 'whatsapp' uses WhatsApp.
 */
class Callssms
{
    /**
     * Open the device's native phone dialer pre-filled with $phone.
     *
     * @param  string  $phone     E.164 or local format, e.g. "+15551234567" or "5551234567"
     * @param  bool    $autoCall  When true attempts to dial immediately (requires CALL_PHONE
     *                            permission; falls back to dialler on denial)
     */
    public function openPhoneDialer(string $phone, bool $autoCall = false): bool
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Callssms.OpenPhoneDialer', json_encode([
                'phone'    => $phone,
                'autoCall' => $autoCall,
            ]));

            if ($result) {
                $decoded = json_decode($result);
                return (bool) ($decoded->data->success ?? false);
            }
        }

        return false;
    }

    /**
     * Open the device's native SMS / MMS app pre-filled with $phone and $message.
     *
     * @param  string  $phone    Recipient phone number
     * @param  string  $message  Pre-filled message body
     */
    public function openSms(string $phone, string $message = ''): bool
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Callssms.OpenSms', json_encode([
                'phone'   => $phone,
                'message' => $message,
            ]));

            if ($result) {
                $decoded = json_decode($result);
                return (bool) ($decoded->data->success ?? false);
            }
        }

        return false;
    }

    /**
     * Open WhatsApp in message or call mode for the given phone number.
     *
     * @param  string  $phone    International format recommended, e.g. "+15551234567"
     * @param  string  $message  Pre-filled message text (ignored when mode is 'call')
     * @param  string  $mode     'message' (default) or 'call'
     */
    public function openWhatsapp(string $phone, string $message = '', string $mode = 'message'): bool
    {
        if (function_exists('nativephp_call')) {
            $result = nativephp_call('Callssms.OpenWhatsapp', json_encode([
                'phone'   => $phone,
                'message' => $message,
                'mode'    => $mode,
            ]));

            if ($result) {
                $decoded = json_decode($result);
                return (bool) ($decoded->data->success ?? false);
            }
        }

        return false;
    }

    /**
     * Launch the contact picker and dispatch a ContactSelected event when the
     * user picks a contact.
     *
     * @param  string  $source  'device' (default) – native contacts
     *                          'whatsapp'          – WhatsApp contact list (Android)
     *                                                or device contacts that open via WhatsApp (iOS)
     */
    public function pickContact(string $source = 'device'): void
    {
        if (function_exists('nativephp_call')) {
            nativephp_call('Callssms.PickContact', json_encode([
                'source' => $source,
            ]));
        }
    }
}
