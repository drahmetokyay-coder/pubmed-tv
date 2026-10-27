import unittest
from datetime import date

import pubmed_tv as t


class Conclusion(unittest.TestCase):
    def test_structured_uses_conclusions_section(self):
        s = [("BACKGROUND", "BACKGROUND", "Bg."), ("CONCLUSIONS", "CONCLUSIONS", "Glue was non-inferior to RFA.")]
        self.assertEqual(t.conclusion(s), "Glue was non-inferior to RFA.")

    def test_label_without_nlm_category(self):
        s = [("Methods", None, "M."), ("Conclusion", None, "EVAR reduced mortality.")]
        self.assertEqual(t.conclusion(s), "EVAR reduced mortality.")

    def test_long_conclusion_takes_first_sentence(self):
        long = "First finding is important. " + "More text here. " * 30
        self.assertEqual(t.conclusion([(None, "CONCLUSIONS", long)]), "First finding is important.")

    def test_strips_funding_trailer(self):
        s = [(None, "CONCLUSIONS", "Stenting lowered stroke risk. (Funded by NHLBI; CREST-2 ClinicalTrials.gov number, NCT02089217.).")]
        self.assertEqual(t.conclusion(s), "Stenting lowered stroke risk.")

    def test_unstructured_takes_last_sentence(self):
        s = [(None, None, "We studied 100 patients. Outcomes were good. Compression is unnecessary.")]
        self.assertEqual(t.conclusion(s), "Compression is unnecessary.")


class Selection(unittest.TestCase):
    def test_new_first_then_year_without_duplicates(self):
        y, n = t.select(["a", "b", "c", "d"], ["b", "e"], n_year=3, n_new=2)
        self.assertEqual(n, ["b", "e"])
        self.assertEqual(y, ["a", "c", "d"])

    def test_scores(self):
        today = date(2026, 10, 27)
        self.assertAlmostEqual(t.year_score(12, date(2026, 4, 27), today), 12 / (183 / 30.44))
        self.assertEqual(t.year_score(5, today, today), 5)  # en az 1 ay
        self.assertEqual(t.new_score("Lancet", ["Randomized Controlled Trial"]), 9 * 1.5)
        self.assertEqual(t.study_type(["Journal Article"]), (None, 1.0))


class Validate(unittest.TestCase):
    def art(self, i, punch="Kısa cümle."):
        return {"pmid": str(i), "url": "u", "topic": "t", "journal": "j", "title": "x",
                "punchTr": punch, "conclusionEn": "c"}

    def test_ok(self):
        self.assertEqual(t.validate([self.art(i) for i in range(100)]), [])

    def test_errors(self):
        arts = [self.art(i) for i in range(99)] + [self.art(1, punch="x" * 141)]
        errs = t.validate(arts)
        self.assertTrue(any("tekrarlanan" in e for e in errs))
        self.assertTrue(any("> 140" in e for e in errs))


if __name__ == "__main__":
    unittest.main()
