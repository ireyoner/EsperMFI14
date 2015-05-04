import java.io.IOException;

import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;

public class Main {
    public static void main(String[] args) throws IOException {
    	EPServiceProvider serviceProvider = EPServiceProviderManager.getDefaultProvider() ;
		EPAdministrator administrator = serviceProvider.getEPAdministrator();
        // Wyliczenie wartosci do wyliczania MFI14
      administrator.createEPL(""
      		+ "insert into for14days "
      		+ "select data as data"
      		+ "     , spolka as spolka"
      		+ "     , case when (wartoscMax + wartoscMin + kursZamkniecia) >= (prev(1, wartoscMax) + prev(1, wartoscMin) + prev(1, kursZamkniecia))"
      		+ "            then obrot * (wartoscMax + wartoscMin + kursZamkniecia) / 3 else 0d end as obrotForsyPlus"
      		+ "     , case when (wartoscMax + wartoscMin + kursZamkniecia) < (prev(1, wartoscMax) + prev(1, wartoscMin) + prev(1, kursZamkniecia))"
      		+ "            then obrot * (wartoscMax + wartoscMin + kursZamkniecia) / 3 else 0d end as obrotForsyMinus"
      		+ "     , (wartoscMax + wartoscMin + kursZamkniecia) / 3 as kursDywergencji "
      		+ "     , kursZamkniecia as kursZamkniecia"
      		+ "  from KursAkcji.std:groupwin(spolka).win:length(2)");

      // Wyliczenie MFI14 (dla 3 dni, zeby szukac max i min)
      administrator.createEPL(""
      		+ "insert into for3days "
      		+ "select data as data"
      		+ "     , spolka as spolka"
      		+ "     , 100d - ( 100d / ( 1d "
      		+ "                 + ((sum(obrotForsyPlus))  "
            + "                   /  (sum(obrotForsyMinus)) )"
            + "                   )) as MFI_14"
            + "       , kursZamkniecia as kursZamkniecia  "
      		+ "  from for14days.std:groupwin(spolka).win:length(14) group by spolka");
      
      
       administrator.createEPL(""
      		+ "insert into extremesBuffer "
      		+ "select f3d.data as data"
      		+ "     , f3d.spolka as spolka"
      		+ "     , f3d.MFI_14 as MFI_14_0"
      		+ "     , prev(1, f3d.MFI_14) as MFI_14_1"
      		+ "     , prev(2, f3d.MFI_14) as MFI_14_2"
      		+ "     , f3d.kursZamkniecia as kursZamkniecia_0"
      		+ "     , prev(1, f3d.kursZamkniecia) as kursZamkniecia_1"
      		+ "     , prev(2, f3d.kursZamkniecia) as kursZamkniecia_2"
      		+ "     , case when prev(2, f3d.kursZamkniecia) > prev(1, f3d.kursZamkniecia) "
      		+ "             and prev(1, f3d.kursZamkniecia) < f3d.kursZamkniecia "
      		+ "            then 'min' "
      		+ "            when prev(2, f3d.kursZamkniecia) < prev(1, f3d.kursZamkniecia) "
      		+ "             and prev(1, f3d.kursZamkniecia) > f3d.kursZamkniecia "
      		+ "            then 'max' "
      		+ "            else 'brak' end as wspDyw "
      		+ "     , case when prev(2, f3d.MFI_14) > prev(1, f3d.MFI_14) "
      		+ "             and prev(1, f3d.MFI_14) < f3d.MFI_14 "
      		+ "            then 'min' "
      		+ "            when prev(2, f3d.MFI_14) < prev(1, f3d.MFI_14) "
      		+ "             and prev(1, f3d.MFI_14) > f3d.MFI_14 "
      		+ "            then 'max' "
      		+ "            else 'brak' end as wspMFI"
      		+ "  from for3days.std:groupwin(spolka).win:length(3) f3d");
      
      //rozdzielenie na dwa okna z samymi ekstremami
     administrator.createEPL(""
     		+ "on extremesBuffer(wspDyw!='brak' OR wspMFI!='brak') as exBuf"
    		 
     		+ " insert into extremeMFI "
     		+ " select exBuf.data as data"
     		+ "      , exBuf.spolka as spolka"
     		+ "      , exBuf.MFI_14_1 as value"
     		+ "      , exBuf.wspDyw as type"
     		+ "  where exBuf.wspDyw !='brak'"
     		
     		+ " insert into extremePrice "
     		+ " select exBuf.data as data, exBuf.spolka as spolka"
     		+ "      , cast(exBuf.kursZamkniecia_1,Double) as value"
     		+ "      , exBuf.wspDyw as type"
     		+ "  where exBuf.wspDyw !='brak'"
     		
     		+ " output all");

     //Tu utknąłem: 
     administrator.createEPL(""
     		+ "insert into trendsMFI "
      		+ "select spolka as spolka"
      		+ "     , data as data"
      		+ "     , case when type='max' then 'sign1' else 'sign2' end as dywTrend "
      		+ "  from extremeMFI.std:groupwin(spolka,type).win:length(2) "
      		+ " where (value > prev(1,value) and type='min') "
      		+ "    or (value < prev(1,value) and type='max') ");
     
     administrator.createEPL(""
     		+ "insert into trendsPrice "
      		+ "select spolka as spolka"
      		+ "     , data as data"
      		+ "     , case when type='max' then 'sign1' else 'sign2' end as dywTrend "
      		+ "  from extremePrice.std:groupwin(spolka,type).win:length(2) "
      		+ " where (value > prev(1,value) and type='max') "
      		+ "    or (value < prev(1,value) and type='min') ");
     
     administrator.createEPL(""
      		+ "insert into MFISignal "
      		+ "select tMFI.spolka as spolka"
      		+ "     , tMFI.data as data"
      		+ "     , case when tMFI.dywTrend='sign1' then '-' else '+' end as signal "
      		+ " from trendsMFI.std:unique(spolka) as tMFI"
      		+ "      inner join"
      		+ "      trendsPrice.std:unique(spolka) as tPrice"
     		+ "   on tMFI.spolka=tPrice.spolka "
     		+ "  and tMFI.data=tPrice.data "
     		+ "  and tMFI.dywTrend=tPrice.dywTrend");

//     EPStatement statement = administrator.createEPL(""
//       		+ "select * from MFISignal");

     administrator.createEPL(""
       		+ "insert into gameSource "
       		+ "select signal.signal as MFISignal"
       		+ "     , ka.kursZamkniecia as kursWymiany"
       		+ "     , ka.data as data"
       		+ "     , ka.spolka as spolka"
       		+ "  from MFISignal.win:length(1) signal"
       		+ "  inner join "
       		+ "       KursAkcji.win:length(1) ka"
       		+ "    on signal.spolka = ka.spolka "
       		+ "   and signal.data = ka.data");

     administrator.createEPL(""
    	        + "create window "
    	        + "  stackGame.std:firstunique(spolka) "
    	        + "    (spolka String"
    	        + "    ,forsa Double"
    	        + "    ,akcje Double"
    	        + "    ,data Date"
    	        + "    )");
     
     administrator.createEPL(""
   	        + " insert into stackGame "
   	        + " select spolka as spolka"
   	        + "      , 1000d as forsa"
   	        + "      , 0d as akcje"
   	        + "      , data as data"
   	        + "   from KursAkcji");

   
   administrator.createEPL(""
  	        + "on gameSource as GS "
  	        + "  merge stackGame as sg "
  	        + "    where GS.spolka = sg.spolka "
  	        + "      when matched and GS.MFISignal = '+' "
  	        + "      then update set "
  	        + "        sg.data = GS.data,"
  	        + "        sg.akcje = sg.akcje + cast(sg.forsa / GS.kursWymiany, int),"
  	        + "        sg.forsa = sg.forsa - cast(sg.forsa / GS.kursWymiany, int) * GS.kursWymiany"
  	        + "      when matched and GS.MFISignal = '-' "
  	        + "      then update set "
  	        + "        sg.data = GS.data,"
  	        + "        sg.forsa = sg.forsa + sg.akcje * GS.kursWymiany,"
  	        + "        sg.akcje = 0d"
  	        );

   EPStatement statement = administrator.createEPL(""
	+ "select sg.spolka as spoka"
	+ "     , sg.akcje as akcje"
	+ "     , sg.forsa as forsa"
//	+ "     , ka.data as data"
	+ "     , ka.kursZamkniecia as kursPrzeliczen"
	+ "     , sg.forsa+sg.akcje*ka.kursZamkniecia as wartosc"
	+ "  from stackGame sg"
	+ "  inner join "
	+ "       KursAkcji( data.getYear() = 2012 "
	+ "              and data.getMonth() = 2 "
	+ "              and data.getDate() = 27"
	+ "                ) as ka unidirectional"
	+ "    on ka.spolka = sg.spolka"
	+ "   and ka.data = sg.data"
	+ "");

      ProstyListener listener = new ProstyListener();
      statement.addListener(listener);

      InputStream inputStream = new InputStream();
      inputStream.generuj(serviceProvider);
    }

}