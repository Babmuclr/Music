import java.lang.invoke.MethodHandles;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.image.WritableImage;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;

import org.apache.commons.math3.complex.Complex;

import jp.ac.kyoto_u.kuis.le4music.Le4MusicUtils;

import java.io.IOException;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.apache.commons.cli.ParseException;

public final class PlotSpectrumCLI extends Application {

private static final Options options = new Options();
private static final String helpMessage =
MethodHandles.lookup().lookupClass().getName() + " [OPTIONS] <WAVFILE>";

static {
/* コ マ ン ド ラ イ ン オ プ シ ョ ン 定 義 */
options.addOption("h", "help", false, "Display this help and exit");

options.addOption("o", "outfile", true,
"Output image file (Default: " +
MethodHandles.lookup().lookupClass().getSimpleName() +
"." + Le4MusicUtils.outputImageExt + ")");
options.addOption(null, "amp-lo", true,
"Lower bound of amplitude [dB] (Default: " +
Le4MusicUtils.spectrumAmplitudeLowerBound + ")");
options.addOption(null, "amp-up", true,
"Upper bound of amplitude [dB] (Default: " +
Le4MusicUtils.spectrumAmplitudeUpperBound + ")");
options.addOption(null, "freq-lo", true,
"Lower bound of frequency [Hz] (Default: 0.0)");
options.addOption(null, "freq-up", true,
"Upper bound of frequency [Hz] (Default: Nyquist)");
}

@Override public final void start(final Stage primaryStage)
throws IOException,
UnsupportedAudioFileException,
ParseException {
/* コ マ ン ド ラ イ ン 引 数 処 理 */
final String[] args = getParameters().getRaw().toArray(new String[0]);
final CommandLine cmd = new DefaultParser().parse(options, args);
if (cmd.hasOption("help")) {
new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
final String[] pargs = cmd.getArgs();
if (pargs.length < 1) {
System.out.println("WAVFILE is not given.");
new HelpFormatter().printHelp(helpMessage, options);
Platform.exit();
return;
}
final File wavFile = new File(pargs[0]);

/* W A V フ ァ イ ル 読 み 込 み */
final AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
final double[] waveform = Le4MusicUtils.readWaveformMonaural(stream);
final AudioFormat format = stream.getFormat();
final double sampleRate = format.getSampleRate();
final double nyquist = sampleRate * 0.5;
stream.close();

/* fftSize = 2ˆp >= waveform.length を 満 た す fftSize を 求 め る
* 2ˆp は シ フ ト 演 算 で 求 め る */
final int fftSize = 1 << Le4MusicUtils.nextPow2(waveform.length);
final int fftSize2 = (fftSize >> 1) + 1;
/* 信 号 の 長 さ を fftSize に 伸 ば し ， 長 さ が 足 り な い 部 分 は0 で 埋 め る ．
* 振 幅 を 信 号 長 で 正 規 化 す る ． */
final double[] src =
Arrays.stream(Arrays.copyOf(waveform, fftSize))
.map(w -> w / waveform.length)
.toArray();
/* 高 速 フ ー リ エ 変 換 を 行 う */
final Complex[] spectrum = Le4MusicUtils.rfft(src);


/* 対 数 振 幅 ス ペ ク ト ル を 求 め る */
final double[] specLog =
Arrays.stream(spectrum)
.mapToDouble(c -> 20.0 * Math.log10(c.abs()))
.toArray();

/* ス ペ ク ト ル 配 列 の 各 要 素 に 対 応 す る 周 波 数 を 求 め る ．
* 以 下 を 満 た す よ う に 線 型 に
* freqs[0] = 0Hz
* freqs[fftSize2 - 1] = sampleRate / 2 (= Nyquist周波数) */
final double[] freqs =
IntStream.range(0, fftSize2)
.mapToDouble(i -> i * sampleRate / fftSize)
.toArray();

/* デ ー タ 系 列 を 作 成 */
final ObservableList<XYChart.Data<Number, Number>> data =
IntStream.range(0, fftSize2)
.mapToObj(i -> new XYChart.Data<Number, Number>(freqs[i], specLog[i]))
.collect(Collectors.toCollection(FXCollections::observableArrayList));

/* デ ー タ 系 列 に 名 前 を つ け る */
final XYChart.Series<Number, Number> series =
new XYChart.Series<>("spectrum", data);

/* X 軸 を 作 成 */
final double freqLowerBound =
Optional.ofNullable(cmd.getOptionValue("freq-lo"))
.map(Double::parseDouble)
.orElse(0.0);
if (freqLowerBound < 0.0)
throw new IllegalArgumentException(
"freq-lo must be non-negative: " + freqLowerBound
);
final double freqUpperBound =
Optional.ofNullable(cmd.getOptionValue("freq-up"))
.map(Double::parseDouble)
.orElse(nyquist);
if (freqUpperBound <= freqLowerBound)
throw new IllegalArgumentException(
"freq-up must be larger than freq-lo: " +
"freq-lo = " + freqLowerBound + ", freq-up = " + freqUpperBound
);
final NumberAxis xAxis = new NumberAxis(
/* axisLabel = */ "Frequency (Hz)",
/* lowerBound = */ freqLowerBound,
/* upperBound = */ freqUpperBound,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(freqUpperBound - freqLowerBound)
);
xAxis.setAnimated(false);

/* Y 軸 を 作 成 */
final double ampLowerBound =
Optional.ofNullable(cmd.getOptionValue("amp-lo"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.spectrumAmplitudeLowerBound);

final double ampUpperBound =
Optional.ofNullable(cmd.getOptionValue("amp-up"))
.map(Double::parseDouble)
.orElse(Le4MusicUtils.spectrumAmplitudeUpperBound);
if (ampUpperBound <= ampLowerBound)
throw new IllegalArgumentException(
"amp-up must be larger than amp-lo: " +
"amp-lo = " + ampLowerBound + ", amp-up = " + ampUpperBound
);
final NumberAxis yAxis = new NumberAxis(
/* axisLabel = */ "Amplitude (dB)",
/* lowerBound = */ ampLowerBound,
/* upperBound = */ ampUpperBound,
/* tickUnit = */ Le4MusicUtils.autoTickUnit(ampUpperBound - ampLowerBound)
);
yAxis.setAnimated(false);

/* チ ャ ー ト を 作 成 */
final LineChart<Number, Number> chart =
new LineChart<>(xAxis, yAxis);
chart.setTitle("Spectrum");
chart.setCreateSymbols(false);
chart.setLegendVisible(false);
chart.getData().add(series);

/* グ ラ フ 描 画 */
final Scene scene = new Scene(chart, 800, 600);
scene.getStylesheets().add("src/le4music.css");

/* ウ イ ン ド ウ 表 示 */
primaryStage.setScene(scene);
primaryStage.setTitle(getClass().getName());
primaryStage.show();

/* チ ャ ー ト を 画 像 フ ァ イ ル へ 出 力 */
Platform.runLater(() -> {
final String[] name_ext = Le4MusicUtils.getFilenameWithImageExt(
Optional.ofNullable(cmd.getOptionValue("outfile")),
getClass().getSimpleName()
);
final WritableImage image = scene.snapshot(null);
try {
ImageIO.write(SwingFXUtils.fromFXImage(image, null),
name_ext[1], new File(name_ext[0] + "." + name_ext[1]));
} catch (IOException e) { e.printStackTrace(); }
});
}

}
