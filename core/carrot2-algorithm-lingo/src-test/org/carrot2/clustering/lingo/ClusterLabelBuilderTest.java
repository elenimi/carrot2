package org.carrot2.clustering.lingo;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Test cases for {@link ClusterLabelBuilder}.
 */
public class ClusterLabelBuilderTest extends TermDocumentMatrixBuilderTestBase
{
    /** Matrix reducer needed for test */
    private TermDocumentMatrixReducer reducer;

    /** Label builder under tests */
    private ClusterLabelBuilder labelBuilder;

    @Before
    public void setUpClusterLabelBuilder()
    {
        labelBuilder = new ClusterLabelBuilder();
        reducer = new TermDocumentMatrixReducer();
    }

    @Test
    public void testEmpty()
    {
        check(new int [0]);
    }

    @Test
    public void testNoPhrases()
    {
        createDocuments("", "aa . aa", "", "bb . bb", "", "cc . cc");
        final int [] expectedFeatureIndex = new int []
        {
            0, 1, 2
        };

        reducer.desiredClusterCount = 3;
        check(expectedFeatureIndex);
    }

    @Test
    public void testSinglePhraseNoSingleWords()
    {
        createDocuments("aa bb", "aa bb", "aa bb", "aa bb");

        final int [] expectedFeatureIndex = new int []
        {
            2
        };

        reducer.desiredClusterCount = 1;
        check(expectedFeatureIndex);
    }

    @Test
    public void testSinglePhraseSingleWords()
    {
        createDocuments("aa bb", "aa bb", "cc", "cc", "aa bb", "aa bb");

        final int [] expectedFeatureIndex = new int []
        {
            2, 3
        };

        reducer.desiredClusterCount = 2;
        check(expectedFeatureIndex);
    }

    private void check(int [] expectedFeatureIndex)
    {
        buildTermDocumentMatrix();
        reducer.reduce(lingoContext);
        labelBuilder.buildLabels(lingoContext, new TfTermWeighting());

        assertThat(lingoContext.clusterLabelFeatureIndex).as("clusterLabelFeatureIndex")
            .containsOnly(expectedFeatureIndex);
    }
}
