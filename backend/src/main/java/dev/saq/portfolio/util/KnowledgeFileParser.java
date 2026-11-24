package dev.saq.portfolio.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.saq.portfolio.dto.internal.KnowledgeFile;

/**
 * Reads one markdown knowledge file into a {@link KnowledgeFile}: YAML-ish frontmatter off the
 * top, body underneath, SHA-256 over the whole thing.
 *
 * <p>The frontmatter parser is deliberately tiny — {@code key: value} lines between two
 * {@code ---} fences, and only {@code title} and {@code url} are read. A full YAML parser would
 * be a dependency and an attack surface for content that this repository writes itself, and the
 * moment a knowledge file needs nested frontmatter is the moment to reconsider the format rather
 * than the parser.
 *
 * <p>Nothing here throws on malformed input. A file with no frontmatter, or with a fence that is
 * never closed, is still perfectly good knowledge: it is read as all-body, given a derived title
 * and no url, and ingested. Failing startup over a missing colon would take the chatbot down to
 * protect a citation link.
 */
public final class KnowledgeFileParser {

	private static final String FENCE = "---";

	private static final String TITLE_KEY = "title:";

	private static final String URL_KEY = "url:";

	private static final String MARKDOWN_SUFFIX = ".md";

	private KnowledgeFileParser() {
	}

	/**
	 * Parses one file.
	 *
	 * @param path    classpath-relative file name, used as the identity and the chunk source
	 * @param content the file's full text, frontmatter included
	 */
	public static KnowledgeFile parse(String path, String content) {
		String normalised = content.replace("\r\n", "\n");
		String hash = sha256(normalised);

		String title = "";
		String url = "";
		String body = normalised;

		if (normalised.startsWith(FENCE + "\n")) {
			// Searching from past the opening fence's newline, not from the fence itself: start a
			// character earlier and a file opening "---\n---" finds its own opening fence as the
			// closing one, leaving a negative-width frontmatter slice.
			int end = normalised.indexOf("\n" + FENCE, FENCE.length() + 1);
			if (end >= 0) {
				String frontmatter = normalised.substring(FENCE.length() + 1, end);
				body = normalised.substring(end + 1 + FENCE.length());
				title = value(frontmatter, TITLE_KEY);
				url = value(frontmatter, URL_KEY);
			}
		}

		return new KnowledgeFile(path, title.isBlank() ? derivedTitle(path, body) : title, url, body.strip(), hash);
	}

	/** The value of one frontmatter key, or empty when it is absent. */
	private static String value(String frontmatter, String key) {
		for (String line : frontmatter.split("\n")) {
			String trimmed = line.strip();
			if (trimmed.regionMatches(true, 0, key, 0, key.length())) {
				return unquote(trimmed.substring(key.length()).strip());
			}
		}
		return "";
	}

	/** Frontmatter values may be quoted; a citation's title must not show the quotes. */
	private static String unquote(String value) {
		if (value.length() >= 2
				&& ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	/** The first markdown heading, or the file name with its extension dropped. */
	private static String derivedTitle(String path, String body) {
		for (String line : body.split("\n")) {
			String trimmed = line.strip();
			if (trimmed.startsWith("#")) {
				String heading = trimmed.replaceFirst("^#+", "").strip();
				if (!heading.isEmpty()) {
					return heading;
				}
			}
		}
		return path.endsWith(MARKDOWN_SUFFIX)
				? path.substring(0, path.length() - MARKDOWN_SUFFIX.length())
				: path;
	}

	/** Hex-encoded SHA-256, the 64 characters {@code knowledge_source.content_hash} holds. */
	private static String sha256(String content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is required of every JVM; this cannot happen.
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

}
