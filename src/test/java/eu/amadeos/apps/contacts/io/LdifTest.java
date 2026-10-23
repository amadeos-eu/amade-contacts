package eu.amadeos.apps.contacts.io;

import eu.amadeos.apps.contacts.domain.Birthday;
import eu.amadeos.apps.contacts.domain.Contact;
import eu.amadeos.apps.contacts.domain.Entry;
import eu.amadeos.apps.contacts.domain.Kind;
import eu.amadeos.apps.contacts.domain.Name;
import eu.amadeos.apps.contacts.domain.PostalAddress;
import eu.amadeos.apps.contacts.domain.StoredContact;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LdifTest {
    @Test
    void readsThunderbird() {
        ImportFile file = Samples.file("thunderbird.ldif");
        assertEquals(FileFormat.LDIF, file.format());
        List<Contact> list = file.contacts(null, Locale.ROOT).stream().map(Imported::contact).toList();
        assertEquals(2, list.size(), "the mailing list is no contact");
        Contact e = list.getFirst();
        assertEquals(Name.of("Elena", "Rossi"), e.name());
        assertEquals("Ele", e.nickname());
        assertEquals(List.of("elena.rossi@example.it", "elena@rossi.example"), e.emails().stream().map(Entry::value).toList());
        assertEquals(List.of(new Entry(Kind.WORK, "+39 02 555 0100"), new Entry(Kind.MOBILE, "+39 333 555 0101")), e.phones());
        assertEquals(List.of(new PostalAddress(Kind.HOME, "Via Garibaldi 7", "20121", "Milano", "", "Italia")), e.addresses());
        assertEquals("Coro di Milano", e.organization());
        assertEquals(new Birthday(1990, 11, 5), e.birthday());
        assertEquals("Liebt Puccini. Speist gern ägaisch.", e.note(), "Base64 values are UTF-8");
        assertEquals(Set.of("Opera friends"), e.groups());
        Contact j = list.get(1);
        assertEquals(List.of("https://jonas.example/blog"), j.websites(), "folded lines are joined");
        assertEquals(Set.of("Opera friends"), j.groups(), "member DNs match whatever the spaces");
    }

    @Test
    void writesWhatItReads() {
        Contact c = new Contact("u", Name.of("Jürgen", "Müller"), "", "", "Grünbau", "Chef",
                List.of(new Entry(Kind.WORK, "+49 911 1"), new Entry(Kind.HOME, "+49 911 2"), new Entry(Kind.MOBILE, "+49 160 3")),
                List.of(new Entry(Kind.WORK, "j@gruenbau.example"), new Entry(Kind.HOME, "j@home.example")),
                List.of(new PostalAddress(Kind.HOME, "Am See 1", "90402", "Nürnberg", "", "Deutschland"),
                        new PostalAddress(Kind.WORK, "Hauptstraße 5", "90403", "Nürnberg", "Bayern", "Deutschland")),
                List.of("https://gruenbau.example"), new Birthday(1965, 12, 24), new TreeSet<>(Set.of("Kunden")),
                " leading space", null);
        String text = Ldif.write(List.of(new StoredContact("p", c)));
        assertTrue(text.contains("cn:: "), "non-ASCII values are Base64: " + text);
        assertTrue(text.contains("objectclass: groupOfNames"));
        Contact back = Ldif.read(text).getFirst();
        assertEquals(c.name(), back.name());
        assertEquals(c.organization(), back.organization());
        assertEquals(Set.copyOf(c.phones()), Set.copyOf(back.phones()));
        assertEquals(c.emails().stream().map(Entry::value).toList(), back.emails().stream().map(Entry::value).toList());
        assertEquals(Set.copyOf(c.addresses()), Set.copyOf(back.addresses()));
        assertEquals(c.birthday(), back.birthday());
        assertEquals(c.groups(), back.groups());
        assertEquals("leading space", back.note());
        assertEquals(c.websites(), back.websites());
    }

    @Test
    void detectsFormats() {
        assertEquals(FileFormat.VCARD_4, FileFormat.detect("\n\nbegin:vcard\nversion:3.0\n"));
        assertEquals(FileFormat.LDIF, FileFormat.detect("# exported\ndn: cn=x\n"));
        assertEquals(FileFormat.LDIF, FileFormat.detect("version: 1\ndn: cn=x\n"));
        assertEquals(FileFormat.CSV, FileFormat.detect("Name,Mail\nA,a@b\n"));
        assertEquals(StandardCharsets.UTF_8, TextFiles.detect("Müller".getBytes(StandardCharsets.UTF_8)));
        assertEquals(TextFiles.WINDOWS_1252, TextFiles.detect("Müller".getBytes(TextFiles.WINDOWS_1252)));
        assertEquals(StandardCharsets.UTF_16, TextFiles.detect("﻿Müller".getBytes(StandardCharsets.UTF_16LE)));
    }
}
