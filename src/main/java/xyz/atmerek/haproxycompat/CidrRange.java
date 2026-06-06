package xyz.atmerek.haproxycompat;

import java.net.InetAddress;
import java.net.UnknownHostException;

// An IPv4/IPv6 CIDR range (e.g. 10.0.0.0/8, ::1/128). A bare IP means a single host.
public final class CidrRange {

    private final byte[] network;
    private final int prefixBits;

    private CidrRange(final byte[] network, final int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    // Parse a CIDR or bare IP. Only IP literals are accepted (no hostnames, so no DNS lookups).
    // Throws IllegalArgumentException on anything invalid.
    public static CidrRange parse(final String spec) {
        final String text = spec.trim();
        final String ipPart;
        int prefix;

        final int slash = text.indexOf('/');
        if (slash >= 0) {
            ipPart = text.substring(0, slash).trim();
            try {
                prefix = Integer.parseInt(text.substring(slash + 1).trim());
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("invalid prefix length");
            }
            if (prefix < 0) {
                throw new IllegalArgumentException("negative prefix length");
            }
        } else {
            ipPart = text;
            prefix = -1;
        }

        if (ipPart.isEmpty() || !isIpLiteral(ipPart)) {
            throw new IllegalArgumentException("not an IP literal");
        }

        final byte[] bytes;
        try {
            bytes = InetAddress.getByName(ipPart).getAddress();
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("not a valid IP address");
        }

        final int maxBits = bytes.length * 8;
        if (prefix < 0) {
            prefix = maxBits;
        }
        if (prefix > maxBits) {
            throw new IllegalArgumentException("prefix length exceeds " + maxBits + " bits");
        }
        return new CidrRange(bytes, prefix);
    }

    // True if the address is in this range (and same family).
    public boolean contains(final InetAddress address) {
        final byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false; // IPv4 vs IPv6
        }
        final int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        final int remainingBits = prefixBits & 7;
        if (remainingBits != 0) {
            final int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
        return true;
    }

    // Guards getByName against DNS: colon means IPv6 literal, otherwise digits and dots only.
    private static boolean isIpLiteral(final String s) {
        if (s.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }
}
