package tutorials.advanced;

import examples.DocumentExtractionExample;
import examples.BatchDocumentExtraction;

/**
 * Advanced tutorial 2:
 * Entry points for the full document workflow examples already in the repo.
 *
 * This file exists so the tutorials folder has a single place to point users
 * to the "full system" examples.
 */
final class FullDocumentWorkflowTutorial {

    private FullDocumentWorkflowTutorial() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Run one of these existing examples:");
        System.out.println("- " + DocumentExtractionExample.class.getName());
        System.out.println("- " + BatchDocumentExtraction.class.getName());
    }
}
