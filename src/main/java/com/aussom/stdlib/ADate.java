/*
 * Copyright 2026 Austin Lehman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aussom.stdlib;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import com.aussom.Util;
import com.aussom.types.*;
import com.aussom.Environment;

/**
 * Aussom Date runtime. Stores a {@link Instant} internally and
 * uses {@link DateTimeFormatter} for parse and format. Hour /
 * minute / second accessors interpret the Instant at UTC; see
 * design/replace-java-date.md for the rationale.
 *
 * Implements {@link Serializable} so external embedders that
 * persist ADate via Java serialization keep working across the
 * migration. The {@code serialVersionUID} is preserved from the
 * pre-migration value.
 */
public class ADate implements AussomTypeObjectInt, AussomTypeInt, Serializable {
	private static final long serialVersionUID = 1579993228939943395L;

	/**
	 * Single zone reference used by every hour/minute/second
	 * accessor on the Date type. UTC is chosen so behaviour is
	 * deterministic across hosts.
	 */
	private static final ZoneId ZONE = ZoneOffset.UTC;

	/**
	 * Canonical toString / JSON-pack output shape. Matches the
	 * pre-migration {@code SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")}
	 * output byte-for-byte for Instants within the supported
	 * range.
	 */
	private static final DateTimeFormatter ISO_OUTPUT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

	/**
	 * The moment in time this Aussom Date represents. Defaults to
	 * the Unix epoch so a freshly constructed Date with no
	 * argument satisfies isEpoch().
	 */
	private Instant instant = Instant.EPOCH;

	public ADate() { }

	public Instant getInstant() {
		return this.instant;
	}

	public void setInstant(Instant instant) {
		this.instant = (instant == null) ? Instant.EPOCH : instant;
	}

	public AussomType newDate(Environment env, ArrayList<AussomType> args) {
		if (!args.get(0).isNull()) {
			long mills = ((AussomInt) args.get(0)).getValue();
			if (mills < 0) {
				this.instant = Instant.now();
			} else {
				this.instant = Instant.ofEpochMilli(mills);
			}
		}
		return env.getClassInstance();
	}

	public AussomType getHours(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getHour());
	}

	public AussomType getMinutes(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getMinute());
	}

	public AussomType getSeconds(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getSecond());
	}

	public AussomType getTime(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.toEpochMilli());
	}

	public AussomType setHours(Environment env, ArrayList<AussomType> args) {
		int h = (int) ((AussomInt) args.get(0)).getValue();
		ZonedDateTime zdt = this.instant.atZone(ZONE);
		this.instant = zdt.withHour(h).toInstant();
		return env.getClassInstance();
	}

	public AussomType setMinutes(Environment env, ArrayList<AussomType> args) {
		int m = (int) ((AussomInt) args.get(0)).getValue();
		ZonedDateTime zdt = this.instant.atZone(ZONE);
		this.instant = zdt.withMinute(m).toInstant();
		return env.getClassInstance();
	}

	public AussomType setSeconds(Environment env, ArrayList<AussomType> args) {
		int s = (int) ((AussomInt) args.get(0)).getValue();
		ZonedDateTime zdt = this.instant.atZone(ZONE);
		this.instant = zdt.withSecond(s).toInstant();
		return env.getClassInstance();
	}

	public AussomType setTime(Environment env, ArrayList<AussomType> args) {
		this.instant = Instant.ofEpochMilli(((AussomInt) args.get(0)).getValue());
		return env.getClassInstance();
	}

	/**
	 * Java-side setter for the underlying epoch milliseconds.
	 * Mirrors the {@code java.util.Date#setTime(long)} signature so
	 * external embedders that called it before the Instant
	 * migration keep working without going through the Aussom
	 * dispatch wrapper.
	 */
	public void setTime(long mills) {
		this.instant = Instant.ofEpochMilli(mills);
	}

	/**
	 * Java-side getter for the underlying epoch milliseconds.
	 * Mirrors the {@code java.util.Date#getTime()} signature so
	 * external embedders that called it before the Instant
	 * migration keep working without going through the Aussom
	 * dispatch wrapper.
	 */
	public long getTime() {
		return this.instant.toEpochMilli();
	}

	public AussomType toString(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.toString());
	}

	/**
	 * Parses DateString using DateFormat. The pattern syntax is
	 * {@link DateTimeFormatter}'s, which is mostly compatible with
	 * the older {@code SimpleDateFormat} syntax. Stricter on
	 * out-of-range values (e.g. 2024-02-30) and on year width
	 * (use {@code uuuu} for years outside 1..9999).
	 *
	 * Strings that omit a zone are interpreted at UTC.
	 */
	public AussomType parse(Environment env, ArrayList<AussomType> args) {
		String text = ((AussomString) args.get(0)).getValueString();
		String pattern = ((AussomString) args.get(1)).getValueString();
		try {
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern).withZone(ZONE);
			this.instant = ZonedDateTime.parse(text, dtf).toInstant();
			return env.getClassInstance();
		} catch (DateTimeParseException e) {
			return new AussomException("Date.parse(): Parse exception. (" + e.getMessage() + ")");
		}
	}

	public AussomType format(Environment env, ArrayList<AussomType> args) {
		String pattern = ((AussomString) args.get(0)).getValueString();
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern(pattern).withZone(ZONE);
		return new AussomString(dtf.format(this.instant));
	}

	public AussomType isEpoch(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.instant.toEpochMilli() == 0L);
	}

	/**
	 * Helper kept for backwards compatibility with any external
	 * embedder that calls it. Returns a new ADate offset by the
	 * provided number of days.
	 */
	public static ADate addDays(ADate dt, int numDays) {
		ADate result = new ADate();
		result.setInstant(dt.getInstant().plus(numDays, ChronoUnit.DAYS));
		return result;
	}

	@Override
	public AussomType toJson(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.toString());
	}

	@Override
	public AussomType pack(Environment env, ArrayList<AussomType> args) {
		ArrayList<String> parts = new ArrayList<String>();
		parts.add("\"type\":\"Date\"");
		parts.add("\"value\":\"" + this.toString() + "\"");
		return new AussomString("{" + Util.join(parts, ",") + "}");
	}

	@Override
	public String toString() {
		return ISO_OUTPUT.format(this.instant.atOffset(ZoneOffset.UTC));
	}

	@Override
	public String toString(int Level) {
		return this.toString();
	}

	@Override
	public String str() {
		return this.toString();
	}

	public String str(int Level) {
		return "\"" + this.toString() + "\"";
	}
}
