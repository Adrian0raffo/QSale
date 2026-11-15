import { expect, it } from 'vitest';
import { dateInputError, dateOptionInput, validDay, todayInLima } from './index';
import {
  safeReturnPath,
  signUpSchema,
  signInSchema,
  planSchema,
  utf8Length,
  parseNumberDraft,
} from './index';
it('normalizes a pasted email without changing the password', () => {
  expect(
    signInSchema.parse({ email: '  ANA@Example.com ', password: ' Keep my password ' }),
  ).toEqual({ email: 'ana@example.com', password: ' Keep my password ' });
});
it('rejects external return URLs while preserving private invitation links', () => {
  expect(safeReturnPath('https://evil.test')).toBe('/plans');
  expect(safeReturnPath('//evil.test')).toBe('/plans');
  expect(safeReturnPath('/join/A1B2C3D4')).toBe('/join/A1B2C3D4');
});
it('validates UTF-8 password limits before BCrypt would reject the password', () => {
  expect(utf8Length('A1' + 'ñ'.repeat(36))).toBe(74);
  expect(
    signUpSchema.safeParse({ name: 'Ana', email: 'ana@test.pe', password: 'A1' + 'ñ'.repeat(36) })
      .success,
  ).toBe(false);
});
it('requires a valid participant count and an ordered tentative date range', () => {
  expect(
    planSchema.safeParse({
      name: 'Cena',
      type: 'DINNER',
      minParticipants: 2,
      tentativeStartDate: '2027-02-02',
      tentativeEndDate: '2027-02-01',
    }).success,
  ).toBe(false);
  expect(planSchema.safeParse({ name: 'Cena', type: 'DINNER', minParticipants: 1 }).success).toBe(
    false,
  );
});
it('lets a numeric draft be cleared and replaced without displaying NaN', () => {
  expect(parseNumberDraft('')).toBeNaN();
  expect(parseNumberDraft('5')).toBe(5);
  expect(parseNumberDraft('', true)).toBeNull();
  expect(parseNumberDraft('40.', true)).toBe(40);
  expect(parseNumberDraft('40,50', true)).toBe(40.5);
  expect(parseNumberDraft('.', true)).toBeNaN();
  const invalid = planSchema.safeParse({
    name: 'Cena',
    type: 'DINNER',
    minParticipants: parseNumberDraft(''),
  });
  expect(invalid.success).toBe(false);
  if (!invalid.success)
    expect(invalid.error.issues[0]?.message).toBe('Escribe un número entero entre 2 y 100.');
  expect(
    planSchema.safeParse({
      name: 'Cena',
      type: 'DINNER',
      minParticipants: parseNumberDraft('5'),
      budget: parseNumberDraft('40,50', true),
    }).success,
  ).toBe(true);
});

it('permits a day without inventing an hour and validates real calendar days', () => {
  const day = todayInLima();
  expect(dateInputError(day)).toBe('');
  expect(dateOptionInput(day)).toEqual({ startsOn: day });
  expect(validDay('2027-02-30')).toBe(false);
  expect(dateInputError('2027-02-30')).not.toBe('');
  expect(dateInputError('2099-12-12', '25:00')).not.toBe('');
  expect(dateInputError('2099-12-12', '', '2099-12-13', '15:00')).not.toBe('');
  expect(dateInputError('2099-12-12', '15:00', '2099-12-12', '15:00')).not.toBe('');
  expect(dateOptionInput('2099-12-12', '', '2099-12-13')).toEqual({
    startsOn: '2099-12-12',
    endsOn: '2099-12-13',
  });
  expect(dateOptionInput('2099-12-12', '15:00')).toEqual({
    startsOn: '2099-12-12',
    startsAt: '2099-12-12T15:00',
  });
});
