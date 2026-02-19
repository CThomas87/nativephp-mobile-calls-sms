<?php

namespace Openview\Callssms\Events;

use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Generic completion event – kept for backward compatibility.
 * Prefer ContactSelected for contact-picker results.
 */
class CallssmsCompleted
{
    use Dispatchable, SerializesModels;

    public function __construct(
        public string $result,
        public ?string $id = null,
    ) {}
}
