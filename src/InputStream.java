import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.espertech.esper.client.EPServiceProvider;

public class InputStream {
	
	private static final String DATA_ROZPOCZECIA = "1961-01-01";
	private static final String DATA_ZAKONCZENIA = "2030-01-01";

	private static final int LICZBA_PLIKOW = 12;
	private static final int MAX_LICZBA_BLEDOW = 5;
	private static InformacjeOPliku tablicaInformacjioPlikach[] = new InformacjeOPliku[LICZBA_PLIKOW];
	private static DateFormat df = null;

	public static int waitsec(int n) {
		int t1;
		do {
			t1 = (int) System.currentTimeMillis() / 1000;
		} while (t1 <= n);
		return t1;
	}

	public void generuj(EPServiceProvider serviceProvider) throws IOException {
		Date dataRozpoczecia = null;
		Date dataZakonczenia = null;
		int liczbaBledow = 0;
		String[] splitResult = null;

		tablicaInformacjioPlikach[0] = this.new InformacjeOPliku(
				"files/tableAPPLE_NASDAQ.csv", "Apple", "NASDAQ");
		tablicaInformacjioPlikach[1] = this.new InformacjeOPliku(
				"files/tableCOCACOLA_NYSE.csv", "CocaCola", "NYSE");
		tablicaInformacjioPlikach[2] = this.new InformacjeOPliku(
				"files/tableDISNEY_NYSE.csv", "Disney", "NYSE");
		tablicaInformacjioPlikach[3] = this.new InformacjeOPliku(
				"files/tableFORD_NYSE.csv", "Ford", "NYSE");
		tablicaInformacjioPlikach[4] = this.new InformacjeOPliku(
				"files/tableGOOGLE_NASDAQ.csv", "Google", "NASDAQ");
		tablicaInformacjioPlikach[5] = this.new InformacjeOPliku(
				"files/tableHONDA_NYSE.csv", "Honda", "NYSE");
		tablicaInformacjioPlikach[6] = this.new InformacjeOPliku(
				"files/tableIBM_NASDAQ.csv", "IBM", "NASDAQ");
		tablicaInformacjioPlikach[7] = this.new InformacjeOPliku(
				"files/tableINTEL_NASDAQ.csv", "Intel", "NASDAQ");
		tablicaInformacjioPlikach[8] = this.new InformacjeOPliku(
				"files/tableMICROSOFT_NASDAQ.csv", "Microsoft", "NASDAQ");
		tablicaInformacjioPlikach[9] = this.new InformacjeOPliku(
				"files/tableORACLE_NASDAQ.csv", "Oracle", "NASDAQ");
		tablicaInformacjioPlikach[10] = this.new InformacjeOPliku(
				"files/tablePEPSICO_NYSE.csv", "PepsiCo", "NYSE");
		tablicaInformacjioPlikach[11] = this.new InformacjeOPliku(
				"files/tableYAHOO_NASDAQ.csv", "Yahoo", "NASDAQ");

		ReverseLineReader readers[] = new ReverseLineReader[LICZBA_PLIKOW];

		try {
			for (int i = 0; i < LICZBA_PLIKOW; i++) {
				readers[i] = new ReverseLineReader(new File(
						tablicaInformacjioPlikach[i].getNazwaPliku()), "UTF-8");
			}
		} catch (FileNotFoundException e) {
			System.err.println("Nie odnaleziono pliku!");
			System.exit(1);
		}

		try {
			df = new SimpleDateFormat("yyyy-MM-dd");
			dataRozpoczecia = df.parse(DATA_ROZPOCZECIA);
			dataZakonczenia = df.parse(DATA_ZAKONCZENIA);
		} catch (ParseException e) {
			System.err
					.println("Nie uda³o siê wczytaæ podanych dat rozpoczêcia i zakoñczenia!");
			System.exit(1);
		}

		String linie[] = new String[LICZBA_PLIKOW];

		// Przesuniêcie do pierwszych notowañ z zakresu dat
		for (int i = 0; i < LICZBA_PLIKOW; i++) {
			while ((linie[i] = readers[i].readLine()) != null) {
				splitResult = linie[i].split(",");
				Date dataNotowania = null;
				try {
					dataNotowania = df.parse(splitResult[0]);
					if (dataNotowania.compareTo(dataRozpoczecia) >= 0) {
						break;
					}
				} catch (Exception e) {
				}
			}
		}

		Date iteratorDaty = dataRozpoczecia;

		// int tiktak = waitsec((int) System.currentTimeMillis() / 1000);

		KursAkcji kurs;
		// G³ówna pêtla
		while ((iteratorDaty.compareTo(dataZakonczenia) <= 0)
				&& (liczbaBledow < MAX_LICZBA_BLEDOW)) {

			for (int i = 0; i < LICZBA_PLIKOW; i++) {
				try {
					Date dataNotowania = WyodrebnijDate(linie[i]);
					
					if (dataNotowania == null) { continue;}

					if ( (dataNotowania.compareTo(iteratorDaty) == -1)) {
						// Data ostatnio wczytanego notowania wczeœniejsza ni¿
						// bie¿¹ca data - pobierz kolejne notowanie!
						if ((linie[i] = readers[i].readLine()) != null) {
							dataNotowania = WyodrebnijDate(linie[i]);
						}
					} else if ((dataNotowania.compareTo(iteratorDaty) == 1)) {
						// Data ostatnio wczytanego notowania póŸniejsza ni¿
						// bie¿¹ca data - czekaj!
						continue;
					}

					if ((dataNotowania != null) && (dataNotowania.equals(iteratorDaty))) {
						// Tworzenie obiektu notowania
						splitResult = linie[i].split(",");
						kurs = new KursAkcji(
								tablicaInformacjioPlikach[i].getNazwaSpolki(),
								tablicaInformacjioPlikach[i].getNazwaMarketu(),
								dataNotowania, 
								Float.valueOf(splitResult[1].trim()).floatValue(), 
								Float.valueOf(splitResult[2].trim()).floatValue(),
								Float.valueOf(splitResult[3].trim()).floatValue(), 
								Float.valueOf(splitResult[4].trim()).floatValue(), 
								Float.valueOf(splitResult[5].trim()).floatValue());
						serviceProvider.getEPRuntime().sendEvent(kurs);
						//System.out.println(kurs.toString());
					}
				} catch (Exception e) {
					liczbaBledow++;
					System.err.println("B³¹d parsowania! [" + linie[i]
							+ "]. Po raz: " + liczbaBledow);

					if (liczbaBledow >= MAX_LICZBA_BLEDOW) {
						System.err.println("Za du¿o b³êdów!");
						break;
					}
				}
			}

			// Inkrementacja daty
			iteratorDaty = InkrementujDate(iteratorDaty);
			// tiktak = waitsec(tiktak);
		}
	}

	// Metody pomocnicze
	private static Date InkrementujDate(Date data) {
		Calendar c = Calendar.getInstance();
		c.setTime(data);
		c.add(Calendar.DATE, 1);
		return c.getTime();
	}
	
	private static Date WyodrebnijDate(String linia) {
		String[] splitResult = linia.split(",");
		if (!splitResult[0].equals("Date")) {
			try {
				return df.parse(splitResult[0]);
			} catch (ParseException e) {
			}
		}
		return null;
	}

	// Klasa pomocnicza 
	private class InformacjeOPliku {
		private String nazwaPliku;
		private String nazwaSpolki;
		private String nazwaMarketu;
		
		public InformacjeOPliku(String nazwaPliku, String nazwaSpolki, String nazwaMarketu) {
			this.nazwaPliku = nazwaPliku;
			this.nazwaSpolki = nazwaSpolki;
			this.nazwaMarketu = nazwaMarketu;
		}

		public String getNazwaPliku() {
			return nazwaPliku;
		}

		public String getNazwaSpolki() {
			return nazwaSpolki;
		}

		public String getNazwaMarketu() {
			return nazwaMarketu;
		}
	}

	// Klasa pomocnicza 
	// AUTOR: WhiteFang34
	// �RÓD£O:
	// http://stackoverflow.com/questions/6011345/read-a-file-line-by-line-in-reverse-order
	private class ReverseLineReader {
	    private static final int BUFFER_SIZE = 8192;

	    private final FileChannel channel;
	    private final String encoding;
	    private long filePos;
	    private ByteBuffer buf;
	    private int bufPos;
	    private byte lastLineBreak = '\n';
	    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

	    public ReverseLineReader(File file, String encoding) throws IOException {
	        RandomAccessFile raf = new RandomAccessFile(file, "r");
	        channel = raf.getChannel();
	        filePos = raf.length();
	        this.encoding = encoding;
	    }

	    public String readLine() throws IOException {
	        while (true) {
	            if (bufPos < 0) {
	                if (filePos == 0) {
	                    if (baos == null) {
	                        return null;
	                    }
	                    String line = bufToString();
	                    baos = null;
	                    return line;
	                }

	                long start = Math.max(filePos - BUFFER_SIZE, 0);
	                long end = filePos;
	                long len = end - start;

	                buf = channel.map(FileChannel.MapMode.READ_ONLY, start, len);
	                bufPos = (int) len;
	                filePos = start;
	            }

	            while (bufPos-- > 0) {
	                byte c = buf.get(bufPos);
	                if (c == '\r' || c == '\n') {
	                    if (c != lastLineBreak) {
	                        lastLineBreak = c;
	                        continue;
	                    }
	                    lastLineBreak = c;
	                    return bufToString();
	                }
	                baos.write(c);
	            }
	        }
	    }

	    private String bufToString() throws UnsupportedEncodingException {
	        if (baos.size() == 0) {
	            return "";
	        }

	        byte[] bytes = baos.toByteArray();
	        for (int i = 0; i < bytes.length / 2; i++) {
	            byte t = bytes[i];
	            bytes[i] = bytes[bytes.length - i - 1];
	            bytes[bytes.length - i - 1] = t;
	        }

	        baos.reset();

	        return new String(bytes, encoding);
	    }
	}
	
}
