import { describe, expect, it } from "vitest";
import { medicationSchema, ScheduleSchema } from "../schema/medicationSchema";

const expectMedicationFieldError = (
  parseResult: ReturnType<typeof medicationSchema.safeParse>,
  field: string,
  message?: string
) => {
  expect(parseResult.success).toBe(false);
  if (!parseResult.success) {
    const fieldErrors = parseResult.error.flatten().fieldErrors as Record<string, string[] | undefined>;
    expect(fieldErrors[field]).toBeTruthy();
    if (message) {
      expect(fieldErrors[field]).toContain(message);
    }
  }
};

describe("ScheduleSchema", () => {
  // TC_SCHEDULE_01: Lịch uống thuốc hợp lệ phải parse thành công.
  it("should parse successfully when schedule time and dosage are valid", () => {
    const parseResult = ScheduleSchema.safeParse([{ time: "08:30", dosage: 1 }]);

    expect(parseResult.success).toBe(true);
  });

  // TC_SCHEDULE_02: time không đúng định dạng HH:mm phải báo lỗi.
  it("should reject schedule time when it does not match HH:mm format", () => {
    const parseResult = ScheduleSchema.safeParse([{ time: "8:30", dosage: 1 }]);

    expect(parseResult.success).toBe(false);
  });

  // TC_SCHEDULE_03: dosage nhỏ hơn 0.25 phải báo lỗi.
  it("should reject schedule dosage smaller than 0.25", () => {
    const parseResult = ScheduleSchema.safeParse([{ time: "08:30", dosage: 0 }]);

    expect(parseResult.success).toBe(false);
  });

  // TC_SCHEDULE_04: time dạng 24:99 vẫn hợp lệ theo regex hiện tại vì schema chỉ kiểm tra HH:mm.
  it("should accept regex-matching time even when clock value is not realistic", () => {
    const parseResult = ScheduleSchema.safeParse([{ time: "24:99", dosage: 1 }]);

    expect(parseResult.success).toBe(true);
  });
});

describe("medicationSchema", () => {
  const validMedicationInput = {
    drug_name: "Paracetamol",
    unit_id: 1,
    start_date: new Date(2026, 3, 29),
    frequency_type: "DAILY" as const,
    days_of_week: [],
    schedules: [{ time: "08:30", dosage: 1 }],
  };

  // TC_MED_01: Dữ liệu thuốc tần suất DAILY hợp lệ phải parse thành công.
  it("should parse successfully when daily medication input is valid", () => {
    const parseResult = medicationSchema.safeParse(validMedicationInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data.interval_days).toBe(undefined);
    }
  });

  // TC_MED_02: Các field optional drug_id, end_date, note có thể được truyền vào hợp lệ.
  it("should parse successfully when optional drug_id end_date and note are provided", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      drug_id: 10,
      end_date: new Date(2026, 4, 29),
      note: "Uống sau ăn",
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MED_03: drug_name rỗng phải báo lỗi bắt buộc nhập tên thuốc.
  it("should return required error when drug_name is empty", () => {
    const parseResult = medicationSchema.safeParse({ ...validMedicationInput, drug_name: "" });

    expectMedicationFieldError(parseResult, "drug_name", "Hãy cung cấp tên thuốc");
  });

  // TC_MED_04: unit_id bị thiếu phải báo lỗi.
  it("should reject medication input when unit_id is missing", () => {
    const { unit_id, ...inputWithoutUnitId } = validMedicationInput;
    const parseResult = medicationSchema.safeParse(inputWithoutUnitId as any);

    expectMedicationFieldError(parseResult, "unit_id");
  });

  // TC_MED_05: start_date không phải Date object phải báo lỗi.
  it("should reject medication input when start_date is not a Date object", () => {
    const parseResult = medicationSchema.safeParse({ ...validMedicationInput, start_date: "2026-04-29" as any });

    expectMedicationFieldError(parseResult, "start_date");
  });

  // TC_MED_06: frequency_type ngoài enum phải báo lỗi.
  it("should reject unsupported frequency_type values", () => {
    const parseResult = medicationSchema.safeParse({ ...validMedicationInput, frequency_type: "MONTHLY" as any });

    expectMedicationFieldError(parseResult, "frequency_type");
  });

  // TC_MED_07: schedules rỗng phải báo lỗi bắt buộc thêm thời gian nhắc nhở.
  it("should return required reminder error when schedules is empty", () => {
    const parseResult = medicationSchema.safeParse({ ...validMedicationInput, schedules: [] });

    expectMedicationFieldError(parseResult, "schedules", "Hãy thêm thời gian nhắc nhở");
  });

  // TC_MED_08: schedule time sai định dạng phải báo lỗi trong schedules.
  it("should reject medication input when a schedule time is invalid", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      schedules: [{ time: "8:30", dosage: 1 }],
    });

    expectMedicationFieldError(parseResult, "schedules");
  });

  // TC_MED_09: schedule dosage nhỏ hơn 0.25 phải báo lỗi trong schedules.
  it("should reject medication input when a schedule dosage is smaller than 0.25", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      schedules: [{ time: "08:30", dosage: 0 }],
    });

    expectMedicationFieldError(parseResult, "schedules");
  });

  // TC_MED_10: INTERVAL thiếu interval_days phải báo lỗi interval_days bắt buộc.
  it("should require interval_days when frequency_type is INTERVAL", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "INTERVAL",
    });

    expectMedicationFieldError(parseResult, "interval_days", "interval_days là bắt buộc khi tần suất là INTERVAL");
  });

  // TC_MED_11: INTERVAL có interval_days rỗng phải báo lỗi.
  it("should reject empty interval_days when frequency_type is INTERVAL", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "INTERVAL",
      interval_days: "",
    });

    expectMedicationFieldError(parseResult, "interval_days", "interval_days là bắt buộc khi tần suất là INTERVAL");
  });

  // TC_MED_12: INTERVAL có interval_days bằng 0 phải báo lỗi.
  it("should reject interval_days smaller than 1 when frequency_type is INTERVAL", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "INTERVAL",
      interval_days: "0",
    });

    expectMedicationFieldError(parseResult, "interval_days", "interval_days là bắt buộc khi tần suất là INTERVAL");
  });

  // TC_MED_13: INTERVAL có interval_days không phải số phải báo lỗi.
  it("should reject non numeric interval_days when frequency_type is INTERVAL", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "INTERVAL",
      interval_days: "abc",
    });

    expectMedicationFieldError(parseResult, "interval_days", "interval_days là bắt buộc khi tần suất là INTERVAL");
  });

  // TC_MED_14: INTERVAL có interval_days hợp lệ phải transform sang number.
  it("should transform interval_days to number when frequency_type is INTERVAL", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "INTERVAL",
      interval_days: "2",
    });

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data.interval_days).toBe(2);
    }
  });

  // TC_MED_15: WEEKLY thiếu days_of_week phải báo lỗi chọn ít nhất 1 ngày.
  it("should require at least one day when frequency_type is WEEKLY", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "WEEKLY",
      days_of_week: [],
    });

    expectMedicationFieldError(parseResult, "days_of_week", "Chọn ít nhất 1 ngày trong tuần");
  });

  // TC_MED_16: WEEKLY có ít nhất 1 ngày hợp lệ phải parse thành công.
  it("should parse successfully when weekly medication has selected days", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "WEEKLY",
      days_of_week: ["MONDAY"],
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MED_17: days_of_week chứa giá trị ngoài enum phải báo lỗi.
  it("should reject unsupported days_of_week values", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      frequency_type: "WEEKLY",
      days_of_week: ["FUNDAY"] as any,
    });

    expectMedicationFieldError(parseResult, "days_of_week");
  });

  // TC_MED_18: schedules có nhiều thời gian hợp lệ phải parse thành công.
  it("should parse successfully when schedules has multiple valid reminders", () => {
    const parseResult = medicationSchema.safeParse({
      ...validMedicationInput,
      schedules: [
        { time: "08:30", dosage: 1 },
        { time: "21:00", dosage: 0.5 },
      ],
    });

    expect(parseResult.success).toBe(true);
  });

  // TC_MED_19: Nhiều trường thuốc sai cùng lúc phải gom lỗi theo field.
  it("should collect errors for multiple invalid medication fields", () => {
    const parseResult = medicationSchema.safeParse({
      drug_name: "",
      unit_id: undefined,
      start_date: "bad-date",
      frequency_type: "INTERVAL",
      interval_days: "0",
      days_of_week: [],
      schedules: [],
    } as any);

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.drug_name).toBeTruthy();
      expect(fieldErrors.unit_id).toBeTruthy();
      expect(fieldErrors.start_date).toBeTruthy();
      expect(fieldErrors.schedules).toBeTruthy();
    }
  });
});
