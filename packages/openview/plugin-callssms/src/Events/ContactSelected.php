<?php

namespace Openview\Callssms\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Dispatched by the native layer when the user selects a contact via the
 * contact picker (both device and WhatsApp sources).
 *
 * Listen for this event in your Livewire component:
 *
 *   use Native\Mobile\Attributes\OnNative;
 *   use Openview\Callssms\Events\ContactSelected;
 *
 *   #[OnNative(ContactSelected::class)]
 *   public function handleContactSelected(string $name, string $phone, string $source): void
 *   {
 *       // $source is 'device' or 'whatsapp'
 *       $this->selectedPhone = $phone;
 *       $this->selectedName  = $name;
 *   }
 */
class ContactSelected
{
    use Dispatchable, SerializesModels;

    public function __construct(
        /** Full display name of the selected contact */
        public string $name,
        /** Phone number as stored in the contact (may include formatting) */
        public string $phone,
        /** 'device' or 'whatsapp' – indicates which picker was used */
        public string $source = 'device',
    ) {}
}
