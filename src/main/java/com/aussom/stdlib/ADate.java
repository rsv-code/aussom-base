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
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;

import com.aussom.Util;
import com.aussom.ast.astClass;
import com.aussom.ast.aussomException;
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
			DateTimeFormatter dtf = new DateTimeFormatterBuilder()
				.appendPattern(pattern)
				.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
				.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
				.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
				.toFormatter().withZone(ZONE);
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

	/* ---- Date-component getters ---- */

	public AussomType getYear(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getYear());
	}

	public AussomType getMonth(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getMonthValue());
	}

	public AussomType getDayOfMonth(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getDayOfMonth());
	}

	public AussomType getDayOfWeek(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getDayOfWeek().getValue());
	}

	public AussomType getDayOfYear(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).getDayOfYear());
	}

	public AussomType getWeekOfYear(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
	}

	public AussomType getMilliseconds(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).get(ChronoField.MILLI_OF_SECOND));
	}

	/* ---- Date-component setters (mutate in place) ---- */

	public AussomType setYear(Environment env, ArrayList<AussomType> args) {
		int y = (int) ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.atZone(ZONE).withYear(y).toInstant();
		return env.getClassInstance();
	}

	public AussomType setMonth(Environment env, ArrayList<AussomType> args) {
		int m = (int) ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.atZone(ZONE).withMonth(m).toInstant();
		return env.getClassInstance();
	}

	public AussomType setDayOfMonth(Environment env, ArrayList<AussomType> args) {
		int d = (int) ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.atZone(ZONE).withDayOfMonth(d).toInstant();
		return env.getClassInstance();
	}

	/* ---- Date arithmetic (mutate in place; negative subtracts) ---- */

	public AussomType addYears(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.atZone(ZONE).plusYears(n).toInstant();
		return env.getClassInstance();
	}

	public AussomType addMonths(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.atZone(ZONE).plusMonths(n).toInstant();
		return env.getClassInstance();
	}

	public AussomType addDays(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.plus(n, ChronoUnit.DAYS);
		return env.getClassInstance();
	}

	public AussomType addHours(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.plus(n, ChronoUnit.HOURS);
		return env.getClassInstance();
	}

	public AussomType addMinutes(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.plus(n, ChronoUnit.MINUTES);
		return env.getClassInstance();
	}

	public AussomType addSeconds(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.plus(n, ChronoUnit.SECONDS);
		return env.getClassInstance();
	}

	public AussomType addMillis(Environment env, ArrayList<AussomType> args) {
		long n = ((AussomInt) args.get(0)).getValue();
		this.instant = this.instant.plus(n, ChronoUnit.MILLIS);
		return env.getClassInstance();
	}

	/* ---- Comparison and difference ---- */

	public AussomType isBefore(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.instant.isBefore(otherInstant(args.get(0))));
	}

	public AussomType isAfter(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.instant.isAfter(otherInstant(args.get(0))));
	}

	public AussomType isSame(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.instant.equals(otherInstant(args.get(0))));
	}

	public AussomType compare(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(Integer.signum(this.instant.compareTo(otherInstant(args.get(0)))));
	}

	public AussomType between(Environment env, ArrayList<AussomType> args) {
		Instant other = otherInstant(args.get(0));
		String unit = (args.size() > 1 && !args.get(1).isNull())
			? ((AussomString) args.get(1)).getValueString() : "millis";
		ChronoUnit cu;
		switch (unit) {
			case "millis":  cu = ChronoUnit.MILLIS;  break;
			case "seconds": cu = ChronoUnit.SECONDS; break;
			case "minutes": cu = ChronoUnit.MINUTES; break;
			case "hours":   cu = ChronoUnit.HOURS;   break;
			case "days":    cu = ChronoUnit.DAYS;    break;
			case "weeks":   cu = ChronoUnit.WEEKS;   break;
			case "months":  cu = ChronoUnit.MONTHS;  break;
			case "years":   cu = ChronoUnit.YEARS;   break;
			default:
				return new AussomException("Date.between(): unknown unit '" + unit
					+ "'. Allowed units are millis, seconds, minutes, hours, days, weeks, months, years.");
		}
		return new AussomInt(cu.between(this.instant.atZone(ZONE), other.atZone(ZONE)));
	}

	/* ---- Current time and copying ---- */

	public AussomType now(Environment env, ArrayList<AussomType> args) {
		this.instant = Instant.now();
		return env.getClassInstance();
	}

	public AussomType copy(Environment env, ArrayList<AussomType> args) throws aussomException {
		astClass ac = env.getClassByName("Date");
		AussomObject co = (AussomObject) ac.instantiate(env, false, new AussomList());
		ADate ad = (ADate) co.getExternObject();
		ad.setInstant(this.instant);
		return co;
	}

	/* ---- Day boundaries ---- */

	public AussomType startOfDay(Environment env, ArrayList<AussomType> args) {
		this.instant = this.instant.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant();
		return env.getClassInstance();
	}

	public AussomType endOfDay(Environment env, ArrayList<AussomType> args) {
		this.instant = this.instant.atZone(ZONE).toLocalDate()
			.atTime(23, 59, 59, 999000000).atZone(ZONE).toInstant();
		return env.getClassInstance();
	}

	/* ---- Display and calendar helpers ---- */

	public AussomType getMonthName(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.instant.atZone(ZONE).getMonth().toString());
	}

	public AussomType getDayOfWeekName(Environment env, ArrayList<AussomType> args) {
		return new AussomString(this.instant.atZone(ZONE).getDayOfWeek().toString());
	}

	public AussomType isLeapYear(Environment env, ArrayList<AussomType> args) {
		return new AussomBool(this.instant.atZone(ZONE).toLocalDate().isLeapYear());
	}

	public AussomType daysInMonth(Environment env, ArrayList<AussomType> args) {
		return new AussomInt(this.instant.atZone(ZONE).toLocalDate().lengthOfMonth());
	}

	/**
	 * Internal helper. Extracts the underlying Instant from an Aussom Date
	 * passed as a method argument.
	 */
	private Instant otherInstant(AussomType arg) {
		return ((ADate) ((AussomObject) arg).getExternObject()).getInstant();
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
