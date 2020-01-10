import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.shape.Line;
import jp.ac.kyoto_u.kuis.le4music.LineChartWithSpectrogram;

public class LineChartWithMarker<X, Y> extends LineChartWithSpectrogram<X, Number> {

    private final ObservableList<Data<X, Y>> horizontalMarkers;
    private final ObservableList<Data<X, Y>> verticalMarkers;
    private Line newline;
    
    public double getChartWidth() {
    	return this.getChartChildren().get(1).getBoundsInLocal().getWidth();
    }
    
    public LineChartWithMarker(Axis<X> xAxis, ValueAxis<Number> yAxis) {
        super(xAxis, yAxis);
        horizontalMarkers = FXCollections.observableArrayList(data -> new Observable[]{data.YValueProperty()});
        horizontalMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
        verticalMarkers = FXCollections.observableArrayList(data -> new Observable[]{data.XValueProperty()});
        verticalMarkers.addListener((InvalidationListener) observable -> layoutPlotChildren());
    }

    public void addHorizontalValueMarker(Data<X, Y> marker) {
        Objects.requireNonNull(marker, "the marker must not be null");
        if (horizontalMarkers.contains(marker)) {
            return;
        }
        Line line = new Line();
        line.setStyle("-fx-stroke:green;-fx-stroke-width:1px;");
        marker.setNode(line);
        getPlotChildren().add(line);
        horizontalMarkers.add(marker);
    }

    public void removeHorizontalValueMarker(Data<X, Y> marker) {
        Objects.requireNonNull(marker, "the marker must not be null");
        if (marker.getNode() != null) {
            getPlotChildren().remove(marker.getNode());
            marker.setNode(null);
        }
        horizontalMarkers.remove(marker);
    }

    public void addVerticalValueMarker(Data<X, Y> marker) {
        Objects.requireNonNull(marker, "the marker must not be null");
        if (verticalMarkers.contains(marker)) {
            return;
        }
        Line line = new Line();
        line.setStyle("-fx-stroke:orange;-fx-stroke-width:1px;");
        marker.setNode(line);
        getPlotChildren().add(line);
        verticalMarkers.add(marker);
        newline = line;
    }

    public void removeVerticalValueMarker(Data<X, Y> marker) {
        Objects.requireNonNull(marker, "the marker must not be null");
        if (marker.getNode() != null) {
            getPlotChildren().remove(marker.getNode());
            marker.setNode(null);
        }
        verticalMarkers.remove(marker);
    }
    
    public void moveVerticalValueMarker(double distance) {
        newline.setTranslateX(distance);
    }

    @Override
    protected void layoutPlotChildren() {
        horizontalMarkers.stream().forEach((horizontalMarker) -> {
            drawHorizontalMarker(horizontalMarker);
        });
        verticalMarkers.stream().forEach((verticalMarker) -> {
            drawVerticalMarker(verticalMarker);
        });
        super.layoutPlotChildren();
    }

    private void drawHorizontalMarker(Data<X, Y> horizontalMarker) {
        Line line = (Line) horizontalMarker.getNode();
        line.setStartX(0);
        line.setEndX(getBoundsInLocal().getWidth());
        line.setStartY(getYAxis().getDisplayPosition((Number) horizontalMarker.getYValue()) + 0.5); // 0.5 for crispness
        line.setEndY(line.getStartY());
        line.toFront();
    }

    private void drawVerticalMarker(Data<X, Y> verticalMarker) {
        Line line = (Line) verticalMarker.getNode();
        line.setStartX(getXAxis().getDisplayPosition(verticalMarker.getXValue()) + 0.5);  // 0.5 for crispness
        line.setEndX(line.getStartX());
        line.setStartY(0d);
        line.setEndY(getBoundsInLocal().getHeight());
        line.toFront();
    }

}