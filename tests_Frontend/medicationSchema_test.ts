import { describe, expect, it } from "vitest";
import { FREQUENCY } from "../src/constants/medication";
import { ScheduleSchema, medicationSchema } from "../src/medicationSchema";



describe("ScheduleSchema", () => {
  const validSchedules = [
    {
      time: "08:30",
      dosage: 1,
    },
  ];

  // TC_MS_01: schedule hợp lệ phải parse thành công.
  it("should parse successfully when schedule entries are valid", () => {
    const parseResult = ScheduleSchema.safeParse(validSchedules);

    expect(parseResult.success).toBe(true);
  });

  // TC_MS_02: time sai định dạng HH:MM phải báo lỗi.
  it("should reject a schedule time that does not match HH:MM format", () => {
    const parseResult = ScheduleSchema.safeParse([
      {
        time: "8:30",
        dosage: 1,
      },
    ]);

    expect(parseResult.success).toBe(false);
  });

  // TC_MS_03: dosage nhỏ hơn 0.25 phải báo lỗi.
  it("should reject a schedule dosage smaller than 0.25", () => {
    const parseResult = ScheduleSchema.safeParse([
      {
        time: "08:30",
        dosage: 0.2,
      },
    ]);

    expect(parseResult.success).toBe(false);
  });
});

describe("medicationSchema", () => {
  const validDailyMedicationInput = {
    drug_id: 1,
    drug_name: "Paracetamol",
    unit_id: 2,
    start_date: new Date("2026-01-01"),
    end_date: new Date("2026-01-10"),
    note: "Sau bữa ăn",
    frequency_type: FREQUENCY.DAILY,
    interval_days: undefined,
    days_of_week: [],
    schedules: [
      {
        time: "08:00",
        dosage: 1,
      },
    ],
  };

  // TC_MS_04: medication DAILY hợp lệ phải parse thành công.
  it("should parse successfully when medication data is valid for DAILY frequency", () => {
    const parseResult = medicationSchema.safeParse(validDailyMedicationInput);

    expect(parseResult.success).toBe(true);
  });

  // TC_MS_05: drug_name rỗng phải báo lỗi bắt buộc nhập.
  it("should reject empty drug_name", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      drug_name: "",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.drug_name).toContain("Hãy cung cấp tên thuốc");
    }
  });

  // TC_MS_06: schedules rỗng phải báo lỗi bắt buộc có ít nhất một lịch nhắc.
  it("should reject empty schedules array", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      schedules: [],
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.schedules).toContain("Hãy thêm thời gian nhắc nhở");
    }
  });

  // TC_MS_07: INTERVAL hợp lệ phải parse thành công và transform interval_days thành number.
  it("should parse successfully for INTERVAL frequency and transform interval_days into number", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.INTERVAL,
      interval_days: "2",
    });

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data.interval_days).toBe(2);
      expect(typeof parseResult.data.interval_days).toBe("number");
    }
  });

  // TC_MS_08: INTERVAL thiếu interval_days phải báo lỗi refine.
  it("should reject INTERVAL frequency when interval_days is missing", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.INTERVAL,
      interval_days: undefined,
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.interval_days).toContain(
        "interval_days là bắt buộc khi tần suất là INTERVAL"
      );
    }
  });

  // TC_MS_09: INTERVAL với interval_days < 1 phải báo lỗi refine.
  it("should reject INTERVAL frequency when interval_days is less than 1", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.INTERVAL,
      interval_days: "0",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.interval_days).toContain(
        "interval_days là bắt buộc khi tần suất là INTERVAL"
      );
    }
  });

  // TC_MS_10: WEEKLY hợp lệ với ít nhất một ngày trong tuần phải parse thành công.
  it("should parse successfully for WEEKLY frequency when days_of_week has at least one value", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.WEEKLY,
      days_of_week: ["MONDAY", "FRIDAY"],
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MS_11: WEEKLY nhưng không chọn ngày nào phải báo lỗi refine.
  it("should reject WEEKLY frequency when days_of_week is empty", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.WEEKLY,
      days_of_week: [],
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.days_of_week).toContain("Chọn ít nhất 1 ngày trong tuần");
    }
  });

  // TC_MS_12: end_date là optional nên được phép vắng mặt.
  it("should allow end_date to be omitted", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      end_date: undefined,
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MS_13: note là optional nên được phép vắng mặt.
  it("should allow note to be omitted", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      note: undefined,
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MS_14: drug_id là optional nên được phép vắng mặt.
  it("should allow drug_id to be omitted", () => {
    const { drug_id, ...medicationWithoutDrugId } = validDailyMedicationInput;
    const parseResult = medicationSchema.safeParse(medicationWithoutDrugId);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data.drug_id).toBeUndefined();
    }
  });

  // TC_MS_15: days_of_week chứa giá trị ngoài enum phải báo lỗi.
  it("should reject invalid enum values inside days_of_week", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      frequency_type: FREQUENCY.WEEKLY,
      days_of_week: ["FUNDAY"],
    });

    expect(parseResult.success).toBe(false);
  });

  // TC_MS_16: start_date phải là Date object hợp lệ ở runtime.
  it("should reject start_date when it is not a Date object", () => {
    const parseResult = medicationSchema.safeParse({
      ...validDailyMedicationInput,
      start_date: "2026-01-01",
    });

    expect(parseResult.success).toBe(false);
  });
});
