package com.marketly.whatsapp.session;

/**
 * All possible states a WhatsApp ordering conversation can be in.
 *
 * State transitions:
 *
 *  UNVERIFIED ──(OTP verified)──► IDLE
 *  IDLE ──(order intent detected)──► COLLECTING_ITEMS
 *  COLLECTING_ITEMS ──(DONE / checkout tapped)──► ADDRESS_SELECT
 *  ADDRESS_SELECT ──(address confirmed)──► SLOT_SELECT
 *  SLOT_SELECT ──(slot confirmed)──► AWAITING_PAYMENT
 *  AWAITING_PAYMENT ──(payment webhook received)──► ORDER_PLACED
 *  ORDER_PLACED ──(session cleaned up)──► IDLE
 *
 *  CANCEL command → IDLE   (from any state)
 *  STOP  command  → session deleted
 *
 * Any state can handle global commands (STATUS, ORDERS, HELP, MENU, STOP)
 * without disrupting the current flow.
 */
public enum ConversationState {

    /** New number — must complete OTP verification before ordering. */
    UNVERIFIED,

    /** Verified customer with no active order in progress. */
    IDLE,

    /** Customer is adding items; conversation is accumulating cart entries. */
    COLLECTING_ITEMS,

    /** Cart is ready; awaiting the customer to select a delivery address. */
    ADDRESS_SELECT,

    /** Address confirmed; awaiting the customer to choose a delivery slot. */
    SLOT_SELECT,

    /**
     * Order summary shown, payment link sent.
     * Waiting for Razorpay payment webhook or customer selecting COD.
     */
    AWAITING_PAYMENT,

    /**
     * Order has been successfully placed.
     * Session transitions back to IDLE after sending confirmation.
     */
    ORDER_PLACED,

    /**
     * OTP was sent, waiting for the customer to reply with the code.
     * Sub-state of UNVERIFIED used only during the verification handshake.
     */
    AWAITING_OTP
}
