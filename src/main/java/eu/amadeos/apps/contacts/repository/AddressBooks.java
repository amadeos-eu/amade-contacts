package eu.amadeos.apps.contacts.repository;

import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Source;
import eu.amadeos.apps.contacts.domain.StoredContact;
import eu.amadeos.apps.contacts.io.VCards;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The address books in {@code ~/.local/share/amadeos/contacts}: every folder in it is one address book and every
 * {@code .vcf} file in a folder one contact, the layout of a "vdir" that vdirsyncer and khard use too and that
 * CardDAV synchronization fills the same way. A folder's name for people is in its file {@code displayname}.
 *
 * <p>Files are written as vCard 4.0 through a temporary file and a rename, so a crash never leaves half a contact.
 * Files that were changed by another program are read again by {@link #reload()}; a file without a UID gets the
 * one from its file name.
 */
public class AddressBooks {
    private static final Logger LOG = LoggerFactory.getLogger(AddressBooks.class);
    /** The folder of the address book that is made when there is none. */
    public static final String DEFAULT_BOOK = "personal";
    static final String DISPLAY_NAME_FILE = "displayname";

    private final Path root;
    private final Clock clock;
    private final String defaultBookName;
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private final Map<String, String> bookNames = new LinkedHashMap<>();

    /** A contact as stored: its file, when it was last read, and the vCard with everything the file holds. */
    private record Card(String book, Path file, FileTime modified, VCard vcard, Contact contact) {
    }

    /**
     * @param defaultBookName the name of the address book made when the folder has none, in the user's language
     */
    public AddressBooks(Path root, Clock clock, String defaultBookName) {
        this.root = Objects.requireNonNull(root, "root");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.defaultBookName = defaultBookName;
    }

    public Path root() {
        return root;
    }

    /**
     * Reads every address book again, making the first one if there is none. Files whose modification time is
     * unchanged are not parsed again.
     *
     * @return true if anything changed since the last call
     */
    public synchronized boolean reload() {
        try {
            Files.createDirectories(root);
            List<Path> books = folders();
            if (books.isEmpty()) {
                Path book = root.resolve(DEFAULT_BOOK);
                Files.createDirectories(book);
                Files.writeString(book.resolve(DISPLAY_NAME_FILE), defaultBookName + "\n", StandardCharsets.UTF_8);
                books = List.of(book);
            }
            Map<String, String> names = new LinkedHashMap<>();
            Map<Path, Card> byFile = new HashMap<>();
            cards.values().forEach(c -> byFile.put(c.file(), c));
            Map<String, Card> fresh = new LinkedHashMap<>();
            for (Path book : books) {
                String id = book.getFileName().toString();
                names.put(id, displayName(book));
                for (Path file : vcfFiles(book)) {
                    Card known = byFile.get(file);
                    FileTime modified = Files.getLastModifiedTime(file);
                    Card card = known != null && known.modified().equals(modified) ? known : read(id, file, modified);
                    if (card == null) continue;
                    Card clash = fresh.putIfAbsent(card.contact().uid(), card);
                    if (clash != null) LOG.warn("{} and {} have the same UID; only the first is shown", clash.file(), file);
                }
            }
            boolean changed = !fresh.equals(cards) || !names.equals(bookNames);
            cards.clear();
            cards.putAll(fresh);
            bookNames.clear();
            bookNames.putAll(names);
            return changed;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Path> folders() throws IOException {
        try (Stream<Path> list = Files.list(root)) {
            return list.filter(Files::isDirectory).filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted().toList();
        }
    }

    private static List<Path> vcfFiles(Path book) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(book, "*.vcf")) {
            stream.forEach(files::add);
        }
        files.sort(null);
        return files;
    }

    private static String displayName(Path book) {
        try {
            String name = Files.readString(book.resolve(DISPLAY_NAME_FILE), StandardCharsets.UTF_8).strip();
            if (!name.isEmpty()) return name;
        } catch (IOException e) {
            // no name: the folder's
        }
        return book.getFileName().toString();
    }

    private Card read(String book, Path file, FileTime modified) {
        try (InputStream in = Files.newInputStream(file)) {
            List<VCard> all = VCards.read(in);
            if (all.isEmpty()) {
                LOG.warn("{} holds no vCard", file);
                return null;
            }
            VCard vcard = all.getFirst();
            if (vcard.getUid() == null || vcard.getUid().getValue() == null || vcard.getUid().getValue().isBlank()) {
                vcard.setUid(new ezvcard.property.Uid(uidFromFileName(file)));
            }
            return new Card(book, file, modified, vcard, VCards.toContact(vcard));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read {}: {}", file, e.toString());
            return null;
        }
    }

    private static String uidFromFileName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".vcf".length());
    }

    // ---------------------------------------------------------------- reading

    /** The address books, folder name to the name people see, in folder order. */
    public synchronized List<Source.Book> books() {
        return bookNames.entrySet().stream().map(e -> new Source.Book(e.getKey(), e.getValue())).toList();
    }

    public synchronized List<StoredContact> contacts() {
        return cards.values().stream().map(c -> new StoredContact(c.book(), c.contact())).toList();
    }

    public synchronized Optional<StoredContact> find(String uid) {
        Card card = cards.get(uid);
        return card == null ? Optional.empty() : Optional.of(new StoredContact(card.book(), card.contact()));
    }

    /** The stored vCard of a contact, with all its properties; for export and copy. */
    public synchronized Optional<VCard> vcard(String uid) {
        Card card = cards.get(uid);
        return card == null ? Optional.empty() : Optional.of(new VCard(card.vcard()));
    }

    /** The file of a contact, for drag and drop. */
    public synchronized Optional<Path> file(String uid) {
        return Optional.ofNullable(cards.get(uid)).map(Card::file);
    }

    // ---------------------------------------------------------------- writing

    /**
     * Saves a new or changed contact. A new one goes into {@code book} as {@code <uuid>.vcf}; a changed one keeps
     * its file and every property the address book does not edit.
     */
    public synchronized StoredContact save(String book, Contact contact) {
        return save(book, contact, null);
    }

    /**
     * Like {@link #save(String, Contact)}, starting from an imported vCard for a new contact, so its other
     * properties are kept too.
     */
    public synchronized StoredContact save(String book, Contact contact, VCard imported) {
        Card known = cards.get(contact.uid());
        String bookId = known != null ? known.book() : book;
        if (!bookNames.containsKey(bookId)) throw new IllegalArgumentException("No address book " + bookId);
        VCard vcard = known != null ? new VCard(known.vcard()) : imported != null ? new VCard(imported) : new VCard();
        VCards.update(vcard, contact, clock.instant());
        Path file = known != null ? known.file() : root.resolve(bookId).resolve(fileName(contact.uid()));
        String text = VCards.write(List.of(vcard), VCardVersion.V4_0);
        try {
            writeAtomically(file, text);
            Card card = new Card(bookId, file, Files.getLastModifiedTime(file), vcard, VCards.toContact(vcard));
            cards.put(contact.uid(), card);
            return new StoredContact(bookId, card.contact());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deletes a contact's file; false if it was already gone. */
    public synchronized boolean delete(String uid) {
        Card card = cards.remove(uid);
        if (card == null) return false;
        try {
            return Files.deleteIfExists(card.file());
        } catch (IOException e) {
            cards.put(uid, card);
            throw new UncheckedIOException(e);
        }
    }

    /** A file name from the UID: its letters, digits, dashes and dots; a UUID URN gives {@code <uuid>.vcf}. */
    static String fileName(String uid) {
        String base = uid.startsWith("urn:uuid:") ? uid.substring("urn:uuid:".length()) : uid;
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty() || base.startsWith(".")) base = "contact" + base;
        if (base.length() > 120) base = base.substring(0, 120);
        return base + ".vcf";
    }

    static void writeAtomically(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling("." + file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            throw e;
        }
    }
}
