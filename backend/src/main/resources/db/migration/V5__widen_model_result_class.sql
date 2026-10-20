-- Widens ck_model_results_class to accept the granular defect classes the
-- trained model actually predicts (OK, SCRATCH, DENT, DISCOLORATION, CRACK,
-- MISALIGNMENT) in addition to the original binary values. Downstream logic
-- maps OK -> no defect, anything else -> potentially defective.
ALTER TABLE model_results DROP CONSTRAINT IF EXISTS ck_model_results_class;
ALTER TABLE model_results ADD CONSTRAINT ck_model_results_class CHECK (
    predicted_class IN (
        'GOOD', 'POTENTIALLY_DEFECTIVE',
        'OK', 'SCRATCH', 'DENT', 'DISCOLORATION', 'CRACK', 'MISALIGNMENT'
    )
);
