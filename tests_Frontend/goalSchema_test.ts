import { describe, expect, it } from "vitest";
import { goalSchema } from "../src/goalSchema";

describe("goalSchema", () => {
  const validGoalInput = {
    height_cm: "170",
    weight_kg: "65",
    aim_weight: "60",
    aim_day: "2026-12-31",
    day_of_activities: "4",
    activity_level: "moderately_active" as const,
  };

  // TC_GS_01: Dữ liệu hợp lệ phải được parse thành công.
  it("should parse successfully when all goal fields are valid", () => {
    const parseResult = goalSchema.safeParse(validGoalInput);

    expect(parseResult.success).toBe(true);
    if (parseResult.success) {
      expect(parseResult.data).toEqual(validGoalInput);
    }
  });

  // TC_GS_02: height_cm rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when height_cm is empty", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, height_cm: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.height_cm).toContain("Nhập chiều cao");
    }
  });

  // TC_GS_03: height_cm bằng 0 phải báo lỗi giá trị không hợp lệ.
  it("should return invalid error when height_cm is zero", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, height_cm: "0" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.height_cm).toContain("Chiều cao không hợp lệ");
    }
  });

  // TC_GS_04: height_cm không phải số phải báo lỗi.
  it("should return invalid error when height_cm is not numeric", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, height_cm: "abc" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.height_cm).toContain("Chiều cao không hợp lệ");
    }
  });

  // TC_GS_05: weight_kg rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when weight_kg is empty", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, weight_kg: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.weight_kg).toContain("Nhập cân nặng hiện tại");
    }
  });

  // TC_GS_06: weight_kg âm phải báo lỗi không hợp lệ.
  it("should return invalid error when weight_kg is negative", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, weight_kg: "-1" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.weight_kg).toContain("Cân nặng không hợp lệ");
    }
  });

  // TC_GS_07: aim_weight rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when aim_weight is empty", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, aim_weight: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.aim_weight).toContain("Nhập cân nặng mong muốn");
    }
  });

  // TC_GS_08: aim_weight không phải số phải báo lỗi.
  it("should return invalid error when aim_weight is not numeric", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, aim_weight: "abc" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.aim_weight).toContain("Cân nặng không hợp lệ");
    }
  });

  // TC_GS_09: aim_day rỗng phải báo lỗi bắt buộc nhập.
  it("should return required error when aim_day is empty", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, aim_day: "" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.aim_day).toContain("Nhập ngày đạt mục tiêu");
    }
  });

  // TC_GS_10: day_of_activities bằng 0 là hợp lệ theo ranh giới dưới.
  it("should accept day_of_activities at lower boundary value 0", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, day_of_activities: "0" });

    expect(parseResult.success).toBe(true);
  });

  // TC_GS_11: day_of_activities bằng 7 là hợp lệ theo ranh giới trên.
  it("should accept day_of_activities at upper boundary value 7", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, day_of_activities: "7" });

    expect(parseResult.success).toBe(true);
  });

  // TC_GS_12: day_of_activities nhỏ hơn 0 phải báo lỗi.
  it("should reject day_of_activities smaller than 0", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, day_of_activities: "-1" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.day_of_activities).toContain("Số ngày tập phải từ 0–7");
    }
  });

  // TC_GS_13: day_of_activities lớn hơn 7 phải báo lỗi.
  it("should reject day_of_activities larger than 7", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, day_of_activities: "8" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.day_of_activities).toContain("Số ngày tập phải từ 0–7");
    }
  });

  // TC_GS_14: day_of_activities không phải số phải báo lỗi.
  it("should reject day_of_activities when it is not numeric", () => {
    const parseResult = goalSchema.safeParse({ ...validGoalInput, day_of_activities: "abc" });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      expect(parseResult.error.flatten().fieldErrors.day_of_activities).toContain("Số ngày tập phải từ 0–7");
    }
  });

  // TC_GS_15: mọi activity_level thuộc enum phải hợp lệ.
  it("should accept every supported activity_level enum value", () => {
    const supportedActivityLevels = [
      "sedentary",
      "lightly_active",
      "moderately_active",
      "very_active",
      "extremely_active",
    ] as const;

    for (const activityLevel of supportedActivityLevels) {
      const parseResult = goalSchema.safeParse({ ...validGoalInput, activity_level: activityLevel });
      expect(parseResult.success).toBe(true);
    }
  });

  // TC_GS_16: activity_level ngoài enum phải báo lỗi.
  it("should reject unsupported activity_level values", () => {
    const parseResult = goalSchema.safeParse({
      ...validGoalInput,
      activity_level: "super_active",
    });

    expect(parseResult.success).toBe(false);
  });

  // TC_GS_17: nhiều trường sai cùng lúc phải trả về lỗi cho nhiều field.
  it("should collect errors for multiple invalid goal fields", () => {
    const parseResult = goalSchema.safeParse({
      ...validGoalInput,
      height_cm: "",
      weight_kg: "0",
      aim_weight: "abc",
      aim_day: "",
      day_of_activities: "10",
      activity_level: "invalid_level",
    });

    expect(parseResult.success).toBe(false);
    if (!parseResult.success) {
      const fieldErrors = parseResult.error.flatten().fieldErrors;
      expect(fieldErrors.height_cm).toBeTruthy();
      expect(fieldErrors.weight_kg).toBeTruthy();
      expect(fieldErrors.aim_weight).toBeTruthy();
      expect(fieldErrors.aim_day).toBeTruthy();
      expect(fieldErrors.day_of_activities).toBeTruthy();
      expect(fieldErrors.activity_level).toBeTruthy();
    }
  });
});
