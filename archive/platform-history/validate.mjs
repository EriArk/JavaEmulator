#!/usr/bin/env node
// Run from any working directory:
// node archive/platform-history/validate.mjs
import { readFile, stat } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const groups = [
  "01-public.json",
  "02-phone-runtimes.json",
  "03-pocket-computers.json",
  "04-handhelds-a.json",
  "05-handhelds-b.json",
  "06-maker-handhelds.json"
];
const locales = ["en", "es", "pt-BR", "ru", "fr", "de", "ja", "zh-CN"];
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};
const read = async (name) => JSON.parse(await readFile(resolve(here, name), "utf8"));
const exists = async (path) => {
  try {
    return (await stat(resolve(root, path))).isFile();
  } catch {
    return false;
  }
};
const sameKeys = (actual, expected) =>
  actual.length === expected.length && expected.every((value) => actual.includes(value));

async function main() {
  const vocabulary = await read("localization.json");
  assert(vocabulary.schemaVersion === 1, "Localization schema version must be 1");
  assert(sameKeys(vocabulary.locales, locales), "Localization does not match the app's 8 locales");
  for (const locale of locales) {
    assert(vocabulary.labels?.[locale]?.device, "Missing card labels for " + locale);
    assert(vocabulary.labels?.[locale]?.imageCredit, "Missing image-credit label for " + locale);
    assert(vocabulary.countries?.[locale], "Missing country names for " + locale);
    assert(vocabulary.specValues?.[locale], "Missing specification translations for " + locale);
    assert(vocabulary.periods?.[locale], "Missing date-period translations for " + locale);
  }

  const ids = new Set();
  let count = 0;
  for (const groupFile of groups) {
    const group = await read(groupFile);
    assert(group.schemaVersion === 1, groupFile + ": unsupported schema");
    assert(group.editorialStatus, groupFile + ": missing editorial status");
    assert(sameKeys(group.locales ?? [], locales), groupFile + ": locale list mismatch");
    assert(Array.isArray(group.cards) && group.cards.length, groupFile + ": no cards");
    for (const card of group.cards) {
      const tag = groupFile + ":" + (card.id ?? "<missing-id>");
      assert(/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(card.id ?? ""), tag + ": invalid id");
      assert(!ids.has(card.id), tag + ": duplicate id");
      ids.add(card.id);
      for (const field of ["device", "year", "maker", "country", "image", "imageAttribution"]) {
        assert(typeof card[field] === "string" && card[field].trim(), tag + ": missing " + field);
      }
      assert(await exists(card.image), tag + ": image not found: " + card.image);
      assert(await exists(card.imageAttribution), tag + ": attribution catalog not found");
      assert(Array.isArray(card.sources) && card.sources.length > 0, tag + ": no sources");
      for (const source of card.sources) {
        assert(typeof source === "string" && source.startsWith("https://"), tag + ": source must use HTTPS");
      }
      const isNumericYear = /^\d{4}(?:[–-]\d{4})?$/.test(card.year);
      for (const locale of locales) {
        assert(Object.hasOwn(vocabulary.countries[locale], card.country),
          tag + ": missing " + card.country + " country translation for " + locale);
        assert(isNumericYear || Object.hasOwn(vocabulary.periods[locale], card.year),
          tag + ": missing year-period translation for " + locale);
      }
      const facts = Object.entries(card.specs ?? {});
      assert(facts.length >= 2 && facts.length <= 4, tag + ": expected 2–4 compact facts");
      for (const [key, value] of facts) {
        assert(Object.hasOwn(vocabulary.labels.en, key), tag + ": unknown fact label " + key);
        assert(typeof value === "string" && value.length > 0, tag + ": blank value for " + key);
      }
      assert(sameKeys(Object.keys(card.copy ?? {}), locales), tag + ": incomplete locale coverage");
      for (const locale of locales) {
        assert(typeof card.copy[locale]?.headline === "string" &&
          card.copy[locale].headline.trim().length > 0, tag + ": missing " + locale + " headline");
        assert(typeof card.copy[locale]?.article === "string" &&
          card.copy[locale].article.trim().length > 30, tag + ": missing " + locale + " article");
      }
      const englishWords = card.copy.en.article.trim().split(/\s+/).length;
      assert(englishWords >= 60 && englishWords <= 100,
        tag + ": article is " + englishWords + " English words (expected 60–100)");
      count++;
    }
  }
  assert(count === 32, "Expected 32 researched platform cards, found " + count);
  console.log("Validated " + count + " cards × " + locales.length +
    " locales = " + (count * locales.length) + " complete histories");
  console.log("Image paths, source lists, stable IDs, facts and editorial length checks passed.");
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
