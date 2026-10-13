package gt.umg.svbgua;

import net.sf.jasperreports.engine.JRChart;
import net.sf.jasperreports.engine.JRChartCustomizer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;

/**
 * JFreeChart recorta a "..." toda etiqueta del eje X más ancha que su categoría y
 * JasperReports no expone ese ajuste en el XML; con muchos días las fechas no caben
 * en horizontal, así que se rotan y se les permite el ancho completo.
 */
public final class EtiquetasVerticalesCustomizer implements JRChartCustomizer {

    @Override
    public void customize(JFreeChart chart, JRChart jasperChart) {
        CategoryAxis eje = chart.getCategoryPlot().getDomainAxis();
        eje.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        eje.setMaximumCategoryLabelWidthRatio(1f);
    }
}
