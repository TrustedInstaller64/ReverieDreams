package nota.utils;

import nota.model.Song;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class NBSDecoderTest {
    private static class TrackedInput extends ByteArrayInputStream {
        private boolean closed;

        private TrackedInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] song() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.write(new byte[]{1, 0, 0, 0});
        for (int i = 0; i < 4; i++) output.writeInt(0);
        output.write(new byte[]{(byte) 232, 3});
        output.write(new byte[3 + 20 + 4]);
        output.writeShort(0);
        output.writeByte(0);
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        TrackedInput valid = new TrackedInput(song());
        Song song = NBSDecoder.parse(valid);
        check(song != null, "Valid song was not decoded");
        check(valid.closed, "Successful decode retained the input stream");
        System.out.println("PASS successful song decode closes the input stream");
        TrackedInput invalid = new TrackedInput(new byte[]{1});
        check(NBSDecoder.parse(invalid) == null, "Truncated song was accepted");
        check(invalid.closed, "Failed decode retained the input stream");
        System.out.println("PASS truncated song decode closes the input stream");

        Path file = Files.createTempFile("reverie-decoder-", ".nbs");
        try {
            Files.write(file, song());
            check(NBSDecoder.parse(file.toFile()) != null, "Valid song file was not decoded");
            Files.delete(file);
            Files.write(file, new byte[]{1});
            check(NBSDecoder.parse(file.toFile()) == null, "Truncated song file was accepted");
            Files.delete(file);
            System.out.println("PASS successful and failed file parses allow immediate deletion");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
