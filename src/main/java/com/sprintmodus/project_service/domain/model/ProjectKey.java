package com.sprintmodus.project_service.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.sprintmodus.common_lib.result.Result;

/**
 * The short key a project shows next to its work items (e.g. {@code WAR}): 2 to 10 uppercase letters or digits, starting
 * with a letter. The database enforces the same shape and that keys are unique, even after a project is deleted.
 */
public final class ProjectKey {

	public static final int MAX_LENGTH = 10;

	private static final Pattern SHAPE = Pattern.compile("[A-Z][A-Z0-9]{1,9}");

	private static final String FALLBACK = "PRJ";

	private ProjectKey() {
	}

	/** Uppercases and checks a key the caller chose; the error is a message safe to show. */
	public static Result<String, String> parse(String raw) {
		String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
		if (!SHAPE.matcher(key).matches()) {
			return Result.failure("Use 2 to 10 letters or digits, starting with a letter.");
		}
		return Result.success(key);
	}

	/**
	 * A key derived from a project name: the initials of its words ("Web App Rewrite" gives {@code WAR}), or the first
	 * letters of a single word ("Sprintmodus" gives {@code SPRI}).
	 */
	public static String suggestFrom(String name) {
		String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		List<String> words = new ArrayList<>();
		for (String word : ascii.split("[^A-Za-z0-9]+")) {
			if (!word.isEmpty()) {
				words.add(word.toUpperCase(Locale.ROOT));
			}
		}
		StringBuilder key = new StringBuilder();
		if (words.size() >= 2) {
			for (String word : words.subList(0, Math.min(words.size(), 5))) {
				key.append(word.charAt(0));
			}
		}
		else if (words.size() == 1) {
			key.append(words.getFirst(), 0, Math.min(words.getFirst().length(), 4));
		}
		// a key starts with a letter
		while (!key.isEmpty() && !Character.isLetter(key.charAt(0))) {
			key.deleteCharAt(0);
		}
		if (key.isEmpty()) {
			return FALLBACK;
		}
		return key.length() < 2 ? key + "P" : key.toString();
	}

	/** The {@code attempt}-th candidate for a base key: the base itself first, then {@code BASE2}, {@code BASE3}, ... */
	public static String withSuffix(String base, int attempt) {
		if (attempt <= 1) {
			return base;
		}
		String suffix = String.valueOf(attempt);
		return base.substring(0, Math.min(base.length(), MAX_LENGTH - suffix.length())) + suffix;
	}

}
