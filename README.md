# Hotel-Reservation-system
New Repository
🔹 System Overview

The system is designed using Object-Oriented Programming (OOP) principles with File I/O (CSV files) for persistent storage. It simulates the complete hotel reservation flow: searching, booking, cancelling, payment simulation, and viewing booking details.

🔹 Major Components

Room & RoomType (Domain)

Each hotel room is represented by a Room object.

Categorized into three types: STANDARD, DELUXE, and SUITE.

Each room has:

ID

Room Number

Category (RoomType)

Price per night

Reservation (Domain)

🔹Represents a booking made by a guest.

Contains:

Reservation ID

Room ID

Guest Name

Check-in & Check-out Dates

Total Amount

Payment Transaction ID

Reservation Status (CONFIRMED / CANCELLED)

Payment Status (PAID, REFUNDED)

Refund Transaction ID (if cancelled with refund)

Catalog (Rooms Database / File I/O)

Stores all rooms in rooms.csv.

🔹Functions:

Retrieve all rooms

Search available rooms by date and type

Check for date overlaps with existing reservations

ReservationStore (Reservations Database / File I/O)

Stores all reservations in reservations.csv.

🔹Functions:

Create new reservation

Cancel reservation (with refund if eligible)

Find by ID

List all reservations

PaymentProcessor (Simulated Payments)

Simulates credit card payments.

Uses the Luhn algorithm to validate 16-digit card numbers.

Generates Transaction IDs for payment and refunds.

Returns PaymentResult with success, txnId, and message.

Main Console UI

🔹Provides a menu-driven interface:

Search availability

Book room

Cancel reservation

View reservation details

List all rooms
