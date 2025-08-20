import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class HotelReservationSystem {
    private static final String ROOMS_FILE = "rooms.csv";
    private static final String RES_FILE = "reservations.csv";

    private final Catalog catalog;
    private final ReservationStore store;
    private final PaymentProcessor payments;

    private final Scanner in;

    public HotelReservationSystem() {
        this.catalog = new Catalog(ROOMS_FILE);
        this.store = new ReservationStore(RES_FILE);
        this.payments = new PaymentProcessor();
        this.in = new Scanner(System.in);
        bootstrapIfNeeded();
    }

    public static void main(String[] args) {
        new HotelReservationSystem().run();
    }

    private void run() {
        System.out.println("\n=== Welcome to the Hotel Reservation System ===\n");
        while (true) {
            try {
                System.out.println("1) Search availability");
                System.out.println("2) Book a room");
                System.out.println("3) Cancel a reservation");
                System.out.println("4) View reservation details");
                System.out.println("5) List all rooms");
                System.out.println("6) List all reservations");
                System.out.println("0) Exit\n");
                System.out.print("Choose: ");
                String choice = in.nextLine().trim();
                switch (choice) {
                    case "1": handleSearch(); break;
                    case "2": handleBooking(); break;
                    case "3": handleCancel(); break;
                    case "4": handleViewDetails(); break;
                    case "5": listRooms(); break;
                    case "6": listReservations(); break;
                    case "0":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid choice.\n");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void handleSearch() {
        LocalDate inDate = promptDate("Enter check-in (YYYY-MM-DD): ");
        LocalDate outDate = promptDate("Enter check-out (YYYY-MM-DD): ");
        if (!outDate.isAfter(inDate)) {
            System.out.println("Check-out must be after check-in.\n");
            return;
        }
        RoomType type = promptRoomTypeOptional();
        List<Room> available = catalog.findAvailable(inDate, outDate, type, store.loadAll());
        if (available.isEmpty()) {
            System.out.println("No rooms available for the selected criteria.\n");
        } else {
            System.out.println("Available rooms:");
            for (Room r : available) {
                System.out.printf("- #%d | %s | %s | ₹%.2f/night\n", r.id, r.number, r.type, r.pricePerNight);
            }
            System.out.println();
        }
    }

    private void handleBooking() {
        LocalDate inDate = promptDate("Enter check-in (YYYY-MM-DD): ");
        LocalDate outDate = promptDate("Enter check-out (YYYY-MM-DD): ");
        if (!outDate.isAfter(inDate)) {
            System.out.println("Check-out must be after check-in.\n");
            return;
        }
        RoomType type = promptRoomTypeOptional();
        List<Room> available = catalog.findAvailable(inDate, outDate, type, store.loadAll());
        if (available.isEmpty()) {
            System.out.println("No rooms available for the selected criteria.\n");
            return;
        }
        System.out.println("Available rooms:");
        for (Room r : available) {
            System.out.printf("- ID:%d | Room:%s | %s | ₹%.2f/night\n", r.id, r.number, r.type, r.pricePerNight);
        }
        System.out.print("Enter room ID to book: ");
        int roomId = Integer.parseInt(in.nextLine().trim());
        Room room = catalog.byId(roomId);
        if (room == null || !available.contains(room)) {
            System.out.println("Invalid room selection or not available.\n");
            return;
        }
        System.out.print("Guest name: ");
        String guest = in.nextLine().trim();

        long nights = Duration.between(inDate.atStartOfDay(), outDate.atStartOfDay()).toDays();
        double amount = nights * room.pricePerNight;
        System.out.printf("Total amount for %d nights: ₹%.2f\n", nights, amount);

        
        System.out.print("Enter card number (simulated, 16 digits): ");
        String card = in.nextLine().replaceAll("\\s+", "");
        PaymentResult p = payments.charge(card, amount);
        if (!p.success) {
            System.out.println("Payment failed: " + p.message + "\n");
            return;
        }
        System.out.println("Payment successful, txn: " + p.txnId);

        Reservation res = store.create(roomId, guest, inDate, outDate, amount, p.txnId);
        System.out.println("Reservation CONFIRMED. Your Reservation ID is: " + res.id + "\n");
    }

    private void handleCancel() {
        System.out.print("Enter Reservation ID to cancel: ");
        long id = Long.parseLong(in.nextLine().trim());
        Reservation res = store.findById(id);
        if (res == null) {
            System.out.println("Reservation not found.\n");
            return;
        }
        if (res.status == ResStatus.CANCELLED) {
            System.out.println("Reservation already cancelled.\n");
            return;
        }

        long daysBefore = Duration.between(LocalDate.now().atStartOfDay(), res.checkIn.atStartOfDay()).toDays();
        boolean refundable = daysBefore >= 2;
        String refundTxn = null;
        if (refundable) {
            PaymentResult r = payments.refund(res.paymentTxnId, res.totalAmount);
            if (!r.success) {
                System.out.println("Refund failed: " + r.message + ". Cancellation aborted.\n");
                return;
            }
            refundTxn = r.txnId;
            System.out.println("Refund successful, txn: " + refundTxn);
        } else {
            System.out.println("No refund as per policy (within 2 days of check-in).\n");
        }
        store.cancel(id, refundTxn);
        System.out.println("Reservation cancelled.\n");
    }

    private void handleViewDetails() {
        System.out.print("Enter Reservation ID: ");
        long id = Long.parseLong(in.nextLine().trim());
        Reservation res = store.findById(id);
        if (res == null) {
            System.out.println("Reservation not found.\n");
            return;
        }
        Room room = catalog.byId(res.roomId);
        System.out.println("\n--- Reservation Details ---");
        System.out.println("Reservation ID: " + res.id);
        System.out.println("Guest: " + res.guestName);
        System.out.println("Status: " + res.status);
        System.out.println("Room: " + (room != null ? room.number + " (" + room.type + ")" : ("#" + res.roomId)));
        System.out.println("Check-in: " + res.checkIn);
        System.out.println("Check-out: " + res.checkOut);
        System.out.printf("Amount: ₹%.2f | Payment: %s | Payment Txn: %s\n", res.totalAmount, res.paymentStatus, res.paymentTxnId);
        if (res.refundTxnId != null) System.out.println("Refund Txn: " + res.refundTxnId);
        System.out.println("Created: " + res.createdAt);
        System.out.println("---------------------------\n");
    }

    private void listRooms() {
        System.out.println("\nRooms:");
        for (Room r : catalog.all()) {
            System.out.printf("ID:%d | Room:%s | %s | ₹%.2f/night\n", r.id, r.number, r.type, r.pricePerNight);
        }
        System.out.println();
    }

    private void listReservations() {
        System.out.println("\nReservations:");
        for (Reservation r : store.loadAll()) {
            System.out.printf("ID:%d | Guest:%s | Room:%d | %s to %s | %s | ₹%.2f\n",
                r.id, r.guestName, r.roomId, r.checkIn, r.checkOut, r.status, r.totalAmount);
        }
        System.out.println();
    }

    private LocalDate promptDate(String label) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        while (true) {
            System.out.print(label);
            String s = in.nextLine().trim();
            try {
                return LocalDate.parse(s, f);
            } catch (Exception e) {
                System.out.println("Invalid date. Use YYYY-MM-DD.");
            }
        }
    }

    private RoomType promptRoomTypeOptional() {
        System.out.print("Filter by type? (press Enter for any) [STANDARD/DELUXE/SUITE]: ");
        String s = in.nextLine().trim();
        if (s.isEmpty()) return null;
        try {
            return RoomType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            System.out.println("Unknown type. Showing any type.");
            return null;
        }
    }

    private void bootstrapIfNeeded() {
        try {
            if (!Files.exists(Path.of(ROOMS_FILE))) {
               
                List<Room> seed = List.of(
                    new Room(1, "101", RoomType.STANDARD, 2499.0),
                    new Room(2, "102", RoomType.STANDARD, 2499.0),
                    new Room(3, "201", RoomType.DELUXE, 3999.0),
                    new Room(4, "202", RoomType.DELUXE, 3999.0),
                    new Room(5, "301", RoomType.SUITE, 6999.0)
                );
                catalog.saveAll(seed);
            }
            if (!Files.exists(Path.of(RES_FILE))) {
                Files.createFile(Path.of(RES_FILE));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to bootstrap storage: " + e.getMessage());
        }
    }

    
    enum RoomType { STANDARD, DELUXE, SUITE }

    static class Room {
        final int id;
        final String number;
        final RoomType type;
        final double pricePerNight;
        Room(int id, String number, RoomType type, double pricePerNight) {
            this.id = id; this.number = number; this.type = type; this.pricePerNight = pricePerNight;
        }
        static Room fromCsv(String line) {
            
            String[] p = line.split(",");
            return new Room(Integer.parseInt(p[0]), p[1], RoomType.valueOf(p[2]), Double.parseDouble(p[3]));
        }
        String toCsv() { return id+","+number+","+type+","+pricePerNight; }
        @Override public boolean equals(Object o){return (o instanceof Room) && ((Room)o).id==this.id;}
        @Override public int hashCode(){return Objects.hash(id);}        
    }

    enum ResStatus { CONFIRMED, CANCELLED }
    enum PayStatus { PAID, REFUNDED, FAILED }

    static class Reservation {
        final long id;
        final int roomId;
        final String guestName;
        final LocalDate checkIn;
        final LocalDate checkOut;
        final double totalAmount;
        final LocalDateTime createdAt;
        final String paymentTxnId;
        final PayStatus paymentStatus;
        final ResStatus status;
        final String refundTxnId;

        Reservation(long id, int roomId, String guestName, LocalDate checkIn, LocalDate checkOut,
                    double totalAmount, LocalDateTime createdAt, String paymentTxnId,
                    PayStatus paymentStatus, ResStatus status, String refundTxnId) {
            this.id = id; this.roomId = roomId; this.guestName = guestName;
            this.checkIn = checkIn; this.checkOut = checkOut; this.totalAmount = totalAmount;
            this.createdAt = createdAt; this.paymentTxnId = paymentTxnId;
            this.paymentStatus = paymentStatus; this.status = status; this.refundTxnId = refundTxnId;
        }

        static Reservation fromCsv(String line) {
            
            String[] p = splitCsv(line);
            long id = Long.parseLong(p[0]);
            int roomId = Integer.parseInt(p[1]);
            String guest = p[2];
            LocalDate ci = LocalDate.parse(p[3]);
            LocalDate co = LocalDate.parse(p[4]);
            double amt = Double.parseDouble(p[5]);
            LocalDateTime created = LocalDateTime.parse(p[6]);
            String payTxn = nullIfEmpty(p[7]);
            PayStatus ps = PayStatus.valueOf(p[8]);
            ResStatus rs = ResStatus.valueOf(p[9]);
            String refund = nullIfEmpty(p[10]);
            return new Reservation(id, roomId, guest, ci, co, amt, created, payTxn, ps, rs, refund);
        }

        String toCsv() {
            return joinCsv(new String[] {
                String.valueOf(id), String.valueOf(roomId), guestName,
                checkIn.toString(), checkOut.toString(), String.valueOf(totalAmount),
                createdAt.toString(), emptyIfNull(paymentTxnId), paymentStatus.toString(),
                status.toString(), emptyIfNull(refundTxnId)
            });
        }

        private static String nullIfEmpty(String s){ return (s==null || s.isEmpty())? null : s; }
        private static String emptyIfNull(String s){ return s==null? "" : s; }

        private static String[] splitCsv(String line){
            
            List<String> parts = new ArrayList<>();
            boolean inQuotes = false; StringBuilder cur = new StringBuilder();
            for (int i=0;i<line.length();i++){
                char c=line.charAt(i);
                if (c=='"') inQuotes=!inQuotes;
                else if (c==',' && !inQuotes){ parts.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
            parts.add(cur.toString());
            return parts.toArray(new String[0]);
        }
        private static String joinCsv(String[] fields){
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<fields.length;i++){
                String f = fields[i];
                boolean needQuote = f.contains(",") || f.contains("\"");
                if (needQuote){ sb.append('"').append(f.replace("\"","\"\"")).append('"'); }
                else sb.append(f);
                if (i<fields.length-1) sb.append(',');
            }
            return sb.toString();
        }
    }

   

    static class Catalog {
        private final String file;
        Catalog(String file){ this.file = file; }
        List<Room> all() {
            try {
                List<Room> out = new ArrayList<>();
                for (String line : Files.readAllLines(Path.of(file))) {
                    if (line.isBlank()) continue;
                    out.add(Room.fromCsv(line));
                }
                return out;
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        void saveAll(List<Room> rooms) {
            try (BufferedWriter w = Files.newBufferedWriter(Path.of(file))) {
                for (Room r : rooms) {
                    w.write(r.toCsv()); w.newLine();
                }
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        Room byId(int id) { return all().stream().filter(r->r.id==id).findFirst().orElse(null); }

        List<Room> findAvailable(LocalDate inDate, LocalDate outDate, RoomType type, List<Reservation> allRes) {
            List<Room> rooms = all();
            if (type != null) rooms.removeIf(r -> r.type != type);
            List<Room> available = new ArrayList<>();
            for (Room r : rooms) {
                boolean occupied = false;
                for (Reservation res : allRes) {
                    if (res.status == ResStatus.CANCELLED) continue;
                    if (res.roomId != r.id) continue;
                    if (datesOverlap(inDate, outDate, res.checkIn, res.checkOut)) {
                        occupied = true; break;
                    }
                }
                if (!occupied) available.add(r);
            }
            return available;
        }

        private boolean datesOverlap(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
            
            return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart) && aStart.isBefore(aEnd) && bStart.isBefore(bEnd)
                   && !(aEnd.equals(bStart) || bEnd.equals(aStart));
        }
    }

    static class ReservationStore {
        private final String file;
        ReservationStore(String file){ this.file = file; }

        synchronized List<Reservation> loadAll() {
            try {
                if (!Files.exists(Path.of(file))) return new ArrayList<>();
                List<Reservation> out = new ArrayList<>();
                for (String line : Files.readAllLines(Path.of(file))) {
                    if (line.isBlank()) continue;
                    out.add(Reservation.fromCsv(line));
                }
                return out;
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        synchronized Reservation create(int roomId, String guest, LocalDate ci, LocalDate co, double amount, String payTxn) {
            List<Reservation> all = loadAll();
            long nextId = all.stream().mapToLong(r->r.id).max().orElse(1000) + 1; // start at 1001
            Reservation r = new Reservation(nextId, roomId, guest, ci, co, amount, LocalDateTime.now(),
                    payTxn, PayStatus.PAID, ResStatus.CONFIRMED, null);
            append(r);
            return r;
        }

        synchronized void cancel(long id, String refundTxn) {
            List<Reservation> all = loadAll();
            List<String> lines = new ArrayList<>();
            boolean found = false;
            for (Reservation r : all) {
                if (r.id == id) {
                    found = true;
                    Reservation updated = new Reservation(r.id, r.roomId, r.guestName, r.checkIn, r.checkOut,
                            r.totalAmount, r.createdAt, r.paymentTxnId,
                            refundTxn != null ? PayStatus.REFUNDED : r.paymentStatus,
                            ResStatus.CANCELLED, refundTxn);
                    lines.add(updated.toCsv());
                } else {
                    lines.add(r.toCsv());
                }
            }
            if (!found) throw new RuntimeException("Reservation not found");
            try {
                Files.write(Path.of(file), lines);
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        synchronized Reservation findById(long id) {
            return loadAll().stream().filter(r->r.id==id).findFirst().orElse(null);
        }

        private void append(Reservation r) {
            try (BufferedWriter w = Files.newBufferedWriter(Path.of(file), StandardOpenOption.APPEND)) {
                w.write(r.toCsv()); w.newLine();
            } catch (IOException e) { throw new RuntimeException(e); }
        }
    }

    

    static class PaymentProcessor {
        private final Random random = new Random();
        PaymentResult charge(String cardNumber, double amount) {
            if (amount <= 0) return new PaymentResult(false, null, "Invalid amount");
            if (!isPlausibleCard(cardNumber)) return new PaymentResult(false, null, "Card declined");
            String txn = genTxn("PAY");
            return new PaymentResult(true, txn, "Charged ₹" + String.format("%.2f", amount));
        }
        PaymentResult refund(String paymentTxnId, double amount) {
            if (paymentTxnId == null || paymentTxnId.isEmpty())
                return new PaymentResult(false, null, "Original payment missing");
            String txn = genTxn("RFD");
            return new PaymentResult(true, txn, "Refunded ₹" + String.format("%.2f", amount));
        }
        private boolean isPlausibleCard(String card) {
            if (card == null || !card.matches("\\d{16}")) return false;
            return luhn(card);
        }
        private boolean luhn(String s){
            int sum=0; boolean alt=false;
            for (int i=s.length()-1;i>=0;i--){
                int n=s.charAt(i)-'0';
                if (alt){ n*=2; if (n>9) n-=9; }
                sum+=n; alt=!alt;
            }
            return sum%10==0;
        }
        private String genTxn(String prefix){
            return prefix + "-" + (100000 + random.nextInt(900000)) + "-" + System.currentTimeMillis();
        }
    }

    static class PaymentResult {
        final boolean success; final String txnId; final String message;
        PaymentResult(boolean success, String txnId, String message){ this.success=success; this.txnId=txnId; this.message=message; }
    }
}
