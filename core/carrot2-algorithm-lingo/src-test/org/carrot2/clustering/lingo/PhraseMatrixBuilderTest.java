package org.carrot2.clustering.lingo;

import static org.carrot2.matrix.MatrixAssertions.assertThat;
import static org.fest.assertions.Assertions.assertThat;

import org.fest.assertions.Assertions;
import org.junit.Before;
import org.junit.Test;

import cern.colt.matrix.DoubleMatrix2D;

/**
 * Test cases for phrase matrix building in {@link ClusterLabelBuilder}.
 */
public class PhraseMatrixBuilderTest extends TermDocumentMatrixBuilderTestBase
{
    /** Label builder under tests */
    private ClusterLabelBuilder labelBuilder;

    @Before
    public void setUpClusterLabelBuilder()
    {
        labelBuilder = new ClusterLabelBuilder();
    }

    @Test
    public void testEmpty()
    {
        check(null);
    }

    @Test
    public void testNoPhrases()
    {
        createDocuments("", "aa . bb", "", "bb . cc", "", "aa . cc . cc");
        check(null);
    }

    @Test
    public void testSinglePhraseNoSingleWords()
    {
        createDocuments("", "aa bb cc", "", "aa bb cc", "", "aa bb cc");

        double [][] expectedPhraseMatrixElements = new double [] []
        {
            {
                0.577, 0.577, 0.577
            }
        };

        check(expectedPhraseMatrixElements);
    }

    @Test
    public void testTwoPhrasesNoSingleWords()
    {
        createDocuments("ee ff", "aa bb cc", "ee ff", "aa bb cc", "ee ff", "aa bb cc");

        double [][] expectedPhraseMatrixElements = new double [] []
        {
            {
                0.707, 0.707, 0, 0, 0
            },
            {
                0, 0, 0.577, 0.577, 0.577
            }
        };

        check(expectedPhraseMatrixElements);
    }

    @Test
    public void testSinglePhraseSingleWords()
    {
        createDocuments("", "aa bb cc", "", "aa bb cc", "", "aa bb cc", "",
            "ff . gg . ff . gg");

        double [][] expectedPhraseMatrixElements = new double [] []
        {
            {
                0.577, 0.577, 0.577, 0, 0
            }
        };

        check(expectedPhraseMatrixElements);
    }

    @Test
    public void testSinglePhraseWithStopWord()
    {
        createDocuments("", "aa stop cc", "", "aa stop cc", "", "aa stop cc");

        double [][] expectedPhraseMatrixElements = new double [] []
        {
            {
                0.707, 0.707
            }
        };

        check(expectedPhraseMatrixElements);
    }

    private void check(double [][] expectedPhraseMatrixElements)
    {
        buildTermDocumentMatrix();
        labelBuilder.buildPhraseMatrix(lingoContext, new TfTermWeighting());
        final DoubleMatrix2D phraseMatrix = lingoContext.phraseMatrix;

        if (expectedPhraseMatrixElements == null)
        {
            Assertions.assertThat(phraseMatrix).isNull();
        }
        else
        {
            assertThat(phraseMatrix).isEquivalentTo(expectedPhraseMatrixElements, 0.01);
        }
    }
}
