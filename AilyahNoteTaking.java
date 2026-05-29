import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public class AileeNoteTaking {

    private static final Path USERS_DIR = Paths.get("users");
    private static final String NOTES_FILENAME = "notes.txt";
    private static final String PASS_FILENAME = "password.txt"; // stores hex(salt):hex(hash)
    private static final SimpleDateFormat TIMESTAMP_FMT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static final Scanner SC = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            Files.createDirectories(USERS_DIR);
        } catch (IOException e) {
            System.out.println("Failed to create users directory: " + e.getMessage());
            return;
        }

        System.out.println("=== Ailee Note Taking  ===");

        while (true) {
            System.out.println("\nMain menu:");
            System.out.println("1. Sign up");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choice: ");
            String ch = SC.nextLine().trim();

            try {
                switch (ch) {
                    case "1":
                        signUp();
                        break;
                    case "2":
                        String user = login();
                        if (user != null) userMenu(user);
                        break;
                    case "3":
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid choice. Enter 1, 2 or 3.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    // --------------------- SIGNUP ---------------------
    private static void signUp() throws Exception {
        System.out.println("\n--- Sign Up ---");
        System.out.print("Choose a username: ");
        String username = SC.nextLine().trim();
        if (!isValidUsername(username)) {
            System.out.println("Invalid username. Use only letters, numbers, dash or underscore.");
            return;
        }

        Path userDir = USERS_DIR.resolve(username);
        if (Files.exists(userDir)) {
            System.out.println("Username already exists. Choose another.");
            return;
        }

        System.out.print("Choose a password: ");
        String pass1 = SC.nextLine();
        if (pass1.trim().isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }

        System.out.print("Confirm password: ");
        String pass2 = SC.nextLine();
        if (!pass1.equals(pass2)) {
            System.out.println("Passwords do not match.");
            return;
        }

        // create user folder and save salted hash
        Files.createDirectories(userDir);
        byte[] salt = Crypto.randomSalt();
        byte[] hash = Crypto.sha256(concat(salt, pass1.getBytes(StandardCharsets.UTF_8)));
        String toStore = bytesToHex(salt) + ":" + bytesToHex(hash);
        Files.write(userDir.resolve(PASS_FILENAME), toStore.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);

        // create empty notes file
        Files.createFile(userDir.resolve(NOTES_FILENAME));

        System.out.println("Account created for '" + username + "'. You can login now.");
    }

    // --------------------- LOGIN ---------------------
    private static String login() throws Exception {
        System.out.println("\n--- Login ---");
        System.out.print("Username: ");
        String username = SC.nextLine().trim();
        Path userDir = USERS_DIR.resolve(username);
        if (!Files.exists(userDir) || !Files.isDirectory(userDir)) {
            System.out.println("No such user.");
            return null;
        }

        Path passFile = userDir.resolve(PASS_FILENAME);
        if (!Files.exists(passFile)) {
            System.out.println("User has no password stored (corrupt).");
            return null;
        }

        String stored = new String(Files.readAllBytes(passFile), StandardCharsets.UTF_8).trim();
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            System.out.println("Password file corrupted.");
            return null;
        }
        byte[] salt = hexToBytes(parts[0]);
        byte[] storedHash = hexToBytes(parts[1]);

        int attempts = 0;
        while (attempts < 3) {
            System.out.print("Password: ");
            String input = SC.nextLine();
            byte[] attemptHash = Crypto.sha256(concat(salt, input.getBytes(StandardCharsets.UTF_8)));
            if (MessageDigest.isEqual(storedHash, attemptHash)) {
                System.out.println("Login successful. Welcome, " + username + "!");
                return username;
            }
            attempts++;
            System.out.println("Wrong password. Attempts left: " + (3 - attempts));
        }

        System.out.println("Too many failed attempts. Returning to main menu.");
        return null;
    }

    // --------------------- USER MENU ---------------------
    private static void userMenu(String username) {
        System.out.println("\n=== User Menu (" + username + ") ===");
        Path userDir = USERS_DIR.resolve(username);
        Path notesFile = userDir.resolve(NOTES_FILENAME);

        while (true) {
            System.out.println("\nOptions:");
            System.out.println("1. Add note");
            System.out.println("2. View notes");
            System.out.println("3. Search notes (keyword/date)");
            System.out.println("4. Edit note by ID");
            System.out.println("5. Delete note by ID");
            System.out.println("6. Delete all notes");
            System.out.println("7. Change password");
            System.out.println("8. Logout");
            System.out.print("Choice: ");
            String choice = SC.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        addNoteFlow(notesFile);
                        break;
                    case "2":
                        displayAllNotes(notesFile);
                        break;
                    case "3":
                        searchNotesFlow(notesFile);
                        break;
                    case "4":
                        editNoteFlow(notesFile);
                        break;
                    case "5":
                        deleteNoteFlow(notesFile);
                        break;
                    case "6":
                        deleteAllNotesFlow(notesFile);
                        break;
                    case "7":
                        changePasswordFlow(userDir);
                        break;
                    case "8":
                        System.out.println("Logging out...");
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    // --------------------- NOTES OPERATIONS ---------------------
    private static void addNoteFlow(Path notesFile) throws Exception {
        System.out.println("\nWrite note (single line). Press Enter to save.");
        System.out.print("> ");
        String text = SC.nextLine().trim();
        if (text.isEmpty()) {
            System.out.println("Empty note ignored.");
            return;
        }

        // compute next id
        long nextId = nextNoteId(notesFile);
        String time = TIMESTAMP_FMT.format(new Date());
        String line = nextId + "|" + time + "|" + escapePipes(text);

        Files.write(notesFile, Collections.singletonList(line), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.println("Note saved with ID " + nextId);
    }

    private static void displayAllNotes(Path notesFile) throws Exception {
        if (!Files.exists(notesFile) || Files.size(notesFile) == 0) {
            System.out.println("\nNo notes found.");
            return;
        }
        System.out.println("\n--- Your Notes ---");
        List<String> lines = Files.readAllLines(notesFile, StandardCharsets.UTF_8);
        for (String l : lines) {
            Note n = Note.fromLine(l);
            System.out.println(n.id + " | " + n.timestamp + " | " + n.text);
        }
    }

    private static void searchNotesFlow(Path notesFile) throws Exception {
        if (!Files.exists(notesFile) || Files.size(notesFile) == 0) {
            System.out.println("No notes to search.");
            return;
        }
        System.out.println("\nSearch by (1) keyword or (2) date substring (e.g. 11/2025 or 27/11/2025): ");
        System.out.print("Choose 1 or 2: ");
        String op = SC.nextLine().trim();
        System.out.print("Enter search text: ");
        String query = SC.nextLine().trim().toLowerCase();

        List<Note> results = new ArrayList<>();
        for (String l : Files.readAllLines(notesFile, StandardCharsets.UTF_8)) {
            Note n = Note.fromLine(l);
            if (op.equals("1")) {
                if (n.text.toLowerCase().contains(query)) results.add(n);
            } else {
                // date search in timestamp text
                if (n.timestamp.toLowerCase().contains(query)) results.add(n);
            }
        }

        if (results.isEmpty()) {
            System.out.println("No matching notes found.");
            return;
        }

        System.out.println("\n--- Search results ---");
        for (Note n : results) {
            System.out.println(n.id + " | " + n.timestamp + " | " + n.text);
        }
    }

    private static void editNoteFlow(Path notesFile) throws Exception {
        if (!Files.exists(notesFile) || Files.size(notesFile) == 0) {
            System.out.println("No notes to edit.");
            return;
        }
        System.out.print("Enter ID of note to edit: ");
        String idStr = SC.nextLine().trim();
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID.");
            return;
        }

        List<String> lines = Files.readAllLines(notesFile, StandardCharsets.UTF_8);
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            Note n = Note.fromLine(lines.get(i));
            if (n.id == id) {
                found = true;
                System.out.println("Current: " + n.text);
                System.out.print("Enter new text: ");
                String newText = SC.nextLine().trim();
                if (newText.isEmpty()) {
                    System.out.println("Empty text not allowed. Edit cancelled.");
                    return;
                }
                String newLine = n.id + "|" + n.timestamp + "|" + escapePipes(newText);
                lines.set(i, newLine);
                Files.write(notesFile, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Note updated.");
                break;
            }
        }
        if (!found) System.out.println("Note ID not found.");
    }

    private static void deleteNoteFlow(Path notesFile) throws Exception {
        if (!Files.exists(notesFile) || Files.size(notesFile) == 0) {
            System.out.println("No notes to delete.");
            return;
        }
        System.out.print("Enter ID of note to delete: ");
        String idStr = SC.nextLine().trim();
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID.");
            return;
        }

        List<String> lines = Files.readAllLines(notesFile, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        boolean removed = false;
        for (String l : lines) {
            Note n = Note.fromLine(l);
            if (n.id == id) {
                removed = true;
                continue; // skip to delete
            }
            out.add(l);
        }

        if (!removed) {
            System.out.println("Note ID not found.");
            return;
        }
        Files.write(notesFile, out, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Note deleted.");
    }

    private static void deleteAllNotesFlow(Path notesFile) throws Exception {
        System.out.print("Are you sure you want to delete ALL notes? (yes/no): ");
        String ans = SC.nextLine().trim().toLowerCase();
        if (!ans.equals("yes")) {
            System.out.println("Cancelled.");
            return;
        }
        Files.write(notesFile, Collections.emptyList(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("All notes deleted.");
    }

    // --------------------- CHANGE PASSWORD ---------------------
    private static void changePasswordFlow(Path userDir) throws Exception {
        Path passFile = userDir.resolve(PASS_FILENAME);
        if (!Files.exists(passFile)) {
            System.out.println("Password file missing.");
            return;
        }
        String stored = new String(Files.readAllBytes(passFile), StandardCharsets.UTF_8).trim();
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            System.out.println("Password file corrupt.");
            return;
        }
        byte[] salt = hexToBytes(parts[0]);
        byte[] storedHash = hexToBytes(parts[1]);

        System.out.print("Enter current password: ");
        String current = SC.nextLine();
        byte[] currentHash = Crypto.sha256(concat(salt, current.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(currentHash, storedHash)) {
            System.out.println("Current password incorrect.");
            return;
        }

        System.out.print("Enter new password: ");
        String np1 = SC.nextLine();
        if (np1.trim().isEmpty()) {
            System.out.println("New password cannot be empty.");
            return;
        }
        System.out.print("Confirm new password: ");
        String np2 = SC.nextLine();
        if (!np1.equals(np2)) {
            System.out.println("Passwords do not match.");
            return;
        }

        // create new salt and hash
        byte[] newSalt = Crypto.randomSalt();
        byte[] newHash = Crypto.sha256(concat(newSalt, np1.getBytes(StandardCharsets.UTF_8)));
        // convert byte to hex
        String store = bytesToHex(newSalt) + ":" + bytesToHex(newHash);
        Files.write(passFile, store.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("Password changed successfully.");
    }

    // --------------------- UTILITIES ---------------------
    private static boolean isValidUsername(String u) {
        if (u == null || u.trim().isEmpty()) return false;
        return u.matches("[A-Za-z0-9_-]+");
    }

    private static long nextNoteId(Path notesFile) {
        try {
            if (!Files.exists(notesFile) || Files.size(notesFile) == 0) return 1L;
            long max = 0L;
            for (String l : Files.readAllLines(notesFile, StandardCharsets.UTF_8)) {
                if (l.trim().isEmpty()) continue;
                try {
                    Note n = Note.fromLine(l);
                    if (n.id > max) max = n.id;
                } catch (Exception ignored) {}
            }
            return max + 1L;
        } catch (IOException e) {
            return 1L;
        }
    }

    private static String escapePipes(String s) {
        // we store raw text but avoid interfering with pipe delimiter — replace '|' with '/'
        return s.replace("|", "/");
    }

    // byte helpers
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    // --------------------- NOTE RECORD ---------------------
    private static class Note {
        long id;
        String timestamp;
        String text;

        static Note fromLine(String line) {
            // expected: id|timestamp|text
            String[] parts = line.split("\\|", 3);
            Note n = new Note();
            n.id = Long.parseLong(parts[0]);
            n.timestamp = parts.length > 1 ? parts[1] : "";
            n.text = parts.length > 2 ? parts[2] : "";
            return n;
        }
    }

    // --------------------- CRYPTO HELPERS ---------------------
    private static class Crypto {
        private static final SecureRandom RNG = new SecureRandom();

        static byte[] randomSalt() {
            byte[] s = new byte[16];
            RNG.nextBytes(s);
            return s;
        }

        static byte[] sha256(byte[] input) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return md.digest(input);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
