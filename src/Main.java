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
//      administrator.createEPL(""
//      		+ "create window for14days.std:groupwin(spolka).win:length(14)"
//      		+ "  (data Date"
//      		+ "  ,spolka String"
//      		+ "  ,obrotForsyPlus Double"
//      		+ "  ,obrotForsyMinus Double"
//      		+ "  ,kursDywergencji Double"
////      		+ "  ,kursSredni Double"
//      		+ "  )");
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
//          administrator.createEPL(""
//          		+ "create window for3days.std:groupwin(spolka).win:length(3)"
//          		+ "  (data Date"
//          		+ "  ,spolka String"
//          		+ "  ,MFI_14 Double"
//          		+ "  ,kursDywergencji Double"
//          		+ "  )");
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
          
          administrator.createEPL("create schema ekstrema (data Date, spolka String, value Double, type String)");

          administrator.createEPL(""
        		+ "create window extremeMFI.std:groupwin(spolka,type).win:length(2) as ekstrema");
          administrator.createEPL(""
          		+ "create window extremePrice.std:groupwin(spolka,type).win:length(2) as ekstrema");
          //rozdzielenie na dwa okna z samymi ekstremami
         administrator.createEPL(""
         		+ "on  extremesBuffer(wspDyw!='brak' OR wspMFI!='brak') as exBuf"
         		+ " insert into extremeMFI "
         		+ " select exBuf.data as data, exBuf.spolka as spolka, exBuf.MFI_14_1 as value, exBuf.wspDyw as type"
         		+ " where exBuf.wspDyw !='brak'"
         		+ " insert into extremePrice "
         		+ " select exBuf.data as data, exBuf.spolka as spolka, cast(exBuf.kursZamkniecia_1,Double) as value, exBuf.wspDyw as type"
         		+ " where exBuf.wspDyw !='brak'"
         		+ " output all");
         //Tu utknąłem: 
//         EPStatement statement = administrator.createEPL(""
//         		+ "select eMFI.data, eMFI.spolka, eMFI.value, prev(1,eMFI.value), eMFI.type, ePrice.value, prev(1,ePrice.value), ePrice.type from "
//         		+ " extremeMFI as eMFI"
//         		+ " inner join"
//         		+ " extremePrice as ePrice"
//         		+ " on eMFI.spolka=ePrice.spolka and eMFI.data=ePrice.data");
         EPStatement statement = administrator.createEPL(""
          		+ "select eMFI.data, eMFI.spolka, eMFI.value, eMFI.type, ePrice.value, ePrice.type from"
          		+ " extremeMFI as eMFI"
          		+ " inner join"
          		+ " extremePrice as ePrice"
          		+ " on eMFI.spolka=ePrice.spolka and eMFI.data=ePrice.data");

      ProstyListener listener = new ProstyListener();
      statement.addListener(listener);

        InputStream inputStream = new InputStream();
        inputStream.generuj(serviceProvider);
    }

}