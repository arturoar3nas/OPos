package com.openbravo.pos.printer.tfhka;
import java.util.Date;
import java.util.Calendar;

import tfhka.PrinterException;
import com.openbravo.pos.ticket.TaxInfo;
import com.openbravo.basic.BasicException;
//import com.openbravo.data.gui.JMessageDialog;
//import com.openbravo.data.gui.MessageInf;
import com.openbravo.data.gui.JMessageDialog;
import com.openbravo.data.gui.MessageInf;
import com.openbravo.data.loader.*;
import tfhka.pa.*;

public class Machine {
//Atributos de Clase
	private int Id_machine;
	private String Serial;
	private tfhka.pa.Tfhka FiscalPrinter;
	private Date fecha;
	private int Ultima_FAC;
	private int Ultima_NC;
	private TaxInfo[] Tasas;
	private String Observaciones = "";
	private Session sesion;
	public String msnError = "";
    private boolean isPrimeravez = false;
    private ReportData ReportePC;
    private S1PrinterData S1Estado;
    private S3PrinterData S3Estado;

//Propiedades de Atributos
	public int getId_machine() {
		return Id_machine;
	}

	public String getSerial() {
		return Serial;
	}

	public tfhka.pa.Tfhka getFiscalPrinter() {
		return FiscalPrinter;
	}

	public Date getFecha() {
		return fecha;
	}

	public int getUltima_FAC() {
		return Ultima_FAC;
	}
	
	public int getUltima_NC() {
		return Ultima_NC;
	}

	public TaxInfo[] getTasas() {
		return Tasas;
	}

	public String getObservaciones() {
		return Observaciones;
	}
//Contructores
	public Machine(tfhka.pa.Tfhka tf, Session s)
	{
		this.sesion = s;
		this.FiscalPrinter = tf;
		this.SubirData();
		this.EstablecerMediosPagos();
	}
	

	//M�todos P�blicos
	/*
	 * Inserta un serial a la tabla Machine de la BD
	 * 
	 */
    public int CargarSerial()
    {     	
    	++this.Id_machine;      
       
        
        if(!this.Serial.equals("??????????") ) // && this.Serial.equals("Z1B8000001")
        {        
    	  String Id_Maq = String.valueOf(this.Id_machine);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
          
    	  Object[] values = new Object[] {Id_Maq, this.Serial, this.Ultima_FAC,this.Ultima_NC,this.fecha,this.Observaciones};
          Datas[] datas = new Datas[] {Datas.STRING, Datas.STRING, Datas.INT,Datas.INT,Datas.TIMESTAMP,Datas.STRING};
         
          try
          {
        	 
          new PreparedSentence(this.sesion
              , "INSERT INTO MAQUINA (ID, SERIAL,ID_ULTFACTURA,ID_ULTINC,FECHA,OBSERVACIONES) VALUES (?, ?, ?, ?, ?, ?)"
              , new SerializerWriteBasicExt(datas, new int[] {0, 1, 2, 3, 4, 5})).exec(values);
          }catch(BasicException be)
          {
        	  this.msnError = be.getMessage();
        	  return -1;
          }
          
          this.isPrimeravez = true;
          
          return 1;
          
          }else
        { 
            this.msnError = "La Maquina Detectada no esta Fiscalizada.";
            
            return 0;
        }
    	
    }
    /*
	 * Consultar un serial de la tabla Machine de la BD
	 * 
	 */
    public String ConsultarSerial(String serialNro)
    {
    	String serialReturn = null;
        
        String Id_Maq = String.valueOf(this.Id_machine);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
        
    	try{
            
    	Object[]record = (Object[]) new StaticSentence(this.sesion
                , "SELECT SERIAL FROM MAQUINA WHERE ID = '"+Id_Maq+"' AND SERIAL = ?"
                , SerializerWriteString.INSTANCE
                , new SerializerReadBasic(new Datas[] {Datas.STRING})).find(serialNro);
             
            
    	
       if(record != null)
        {serialReturn = (String) record[0];}
        
    		
    }catch(BasicException be)
    {
  	  this.msnError = be.getMessage();
  	  return null;
    }
        
        
    	return serialReturn;
    }
    /*
	 * Ajustar la fecha-hora de la impresora
	 * 
	 */
    public void AjustarFechaHora() throws PrinterException
    { 
    	Calendar calendario= Calendar.getInstance();
        int hora =calendario.get(Calendar.HOUR_OF_DAY);
       int minuto =calendario.get(Calendar.MINUTE);
       int second = calendario.get(Calendar.SECOND);
         int mes =  calendario.get(Calendar.MONTH);    
         int dia = calendario.get(Calendar.DAY_OF_MONTH); 
         int ano = calendario.get(Calendar.YEAR); 
         mes = mes + 1;
        String Hr = String.valueOf(hora);
        while(Hr.length()<2)
        {
            Hr = "0" + Hr;
        }
         String Mn = String.valueOf(minuto);
         while(Mn.length()<2)
        {
            Mn = "0" + Mn;
        }
          String Seg = String.valueOf(second);
          while(Seg.length()<2)
        {
            Seg = "0" + Seg;
        }
          String Day = String.valueOf(dia);
           while(Day.length()<2)
        {
            Day = "0" + Day;
        }
          String Mess = String.valueOf(mes);
           while(Mess.length()<2)
        {
           Mess = "0" + Mess;
        }
          String Ayear = String.valueOf(ano);
          
          Calendar calendario2= Calendar.getInstance();
          calendario2.setTime(this.fecha);
          int hora2 =calendario.get(Calendar.HOUR_OF_DAY);
          int minuto2 =calendario.get(Calendar.MINUTE);
          int second2 = calendario.get(Calendar.SECOND);
            int mes2 =  calendario.get(Calendar.MONTH);    
            int dia2 = calendario.get(Calendar.DAY_OF_MONTH); 
            int ano2 = calendario.get(Calendar.YEAR); 
            mes2 = mes2 + 1;
            
            boolean bandera = false;
            if(ano == ano2)
            {
            	if(mes == mes2)
            	{
            		if(dia ==  dia2)
            		{
            			if(hora == hora2)
            			{
            				if(minuto == minuto2)
            				{
            					bandera = true;
            				}
            			}
            		}
            	}
            }
            
          
            if(!bandera)
            {// Mando actualizar la fecha a la impresora fiscal
            	
            		// Grabo la Hora
                this.FiscalPrinter.SendCmd("PF" + Hr + Mn + Seg);
                // Grabo la fecha
                this.FiscalPrinter.SendCmd("PG" + Day + Mess + Ayear);
                
               
            }
    }
    /*
	 * Comparar tasas de la impresora con la del sistema
	 * 
	 */
    public boolean CompararTasas()
    {
    	try
    	{
         java.util.List  lista = this.getTaxList().list();    	  
    	  Object[] objs  = lista.toArray();
          TaxInfo[] taxas = new TaxInfo[lista.size()];
          //![0]
          for(int nIndex = 0;nIndex < lista.size();nIndex++) {
              taxas[nIndex] = (TaxInfo)objs[nIndex];          
          }
    	  //![0]
//    	  taxas[0] = (TaxInfo)objs[0];
//    	  taxas[1] = (TaxInfo)objs[1];
//    	  taxas[2] = (TaxInfo)objs[2];
//    	  taxas[3] = (TaxInfo)objs[3];
       
    	  boolean bandera = true;
    	  int i = 0;
    	  for(TaxInfo tax : taxas) {                  
    		  double a = tax.getRate();
    		  double b = this.Tasas[i].getRate();
    		  boolean x = tax.isCascade();
    		  boolean y = this.Tasas[i].isCascade();
    		  
    		  if(tax.getRate() == this.Tasas[i].getRate() && tax.isCascade() == this.Tasas[i].isCascade())
    		  {bandera = bandera && true;}
    		  else
    		  {bandera = bandera && false;}
            
    		  ++i;
         }
    	  
    	  if(bandera && this.isPrimeravez)
    	  {//Guardo las tasas
              
         String Id_Maq = String.valueOf(this.Id_machine);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
    		  
    		  for(TaxInfo tax : taxas)
    	         {
    		  Object[] values = new Object[] {Id_Maq,tax.getId()};
              Datas[] datas = new Datas[] {Datas.STRING, Datas.STRING};
              try
              {
            	 
              new PreparedSentence(this.sesion
                  , "INSERT INTO MAQUINA_TAXES (MAQUINA, TASA) VALUES (?, ?)"
                  , new SerializerWriteBasicExt(datas, new int[] {0, 1})).exec(values);
              }catch(BasicException be)
              {
            	  this.msnError = be.getMessage();
            	 
              }
    	         }
    	  }
    	  
    	  if(!bandera)
    	  {
    		  char[] mode = new char[lista.size()];
                  
                  for(int nIndex = 0;nIndex < lista.size(); nIndex++) {
                    if(taxas[nIndex].isCascade()) {
                        mode[nIndex] = 'I';
                    }
                    else {
                        mode[nIndex] = 'E';
                    }                  
                  }
    		  
//    		  if(taxas[0].isCascade())
//    		  {mode[0] = 'I';}
//    		  else
//    		  {mode[0] = 'E';}
//    		  
//    		  if(taxas[1].isCascade())
//    		  {mode[1] = 'I';}
//    		  else
//    		  {mode[1] = 'E';}
//    		  
//    		  if(taxas[2].isCascade())
//    		  {mode[2] = 'I';}
//    		  else
//    		  {mode[2] = 'E';}
//    		  
//    		  if(taxas[3].isCascade())
//    		  {mode[3] = 'I';}
//    		  else
//    		  {mode[3] = 'E';}
    		  
    		  //!this.msnError = "Configure las Tasas de esta manera: " + taxas[0].getName() + " = " + (taxas[0].getRate())*100 +" %  Modo: " +mode[0] + ", " + taxas[1].getName() + " = " + (taxas[1].getRate())*100 +" %  Modo: " +mode[1] + ", " + taxas[2].getName() + " = " + (taxas[2].getRate())*100 +" %  Modo: " +mode[2] + ", " + taxas[3].getName() + " = " + (taxas[3].getRate())*100 +" %  Modo: " +mode[3] + "."; 
    	  }
    	  
    	  return bandera;
    	}
    	catch(BasicException be)
    	{return false;}
    	
    	
    }
    /*
     * 
     * Modificar Contadores
     */
    public int ModificarContadores()
    {	
    	this.DameContadores();
        
        String Id_Maq = String.valueOf(this.Id_machine);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
    	
    	  Object[] values = new Object[] {Id_Maq, this.Ultima_FAC, this.Ultima_NC};
          Datas[] datas = new Datas[] {Datas.STRING, Datas.INT, Datas.INT};
   	
    	try
         { 
    		new PreparedSentence(this.sesion
    	                  , "UPDATE MAQUINA SET ID_ULTFACTURA = ?, ID_ULTINC = ? WHERE ID = ?"
    	                  , new SerializerWriteBasicExt(datas, new int[] {1, 2, 0})).exec(values);
         }catch(BasicException be)
         {
       	  this.msnError = be.getMessage();
       	  return -1;
         }
         
         return 1;
   		
    }
    /*
     * 
     * Comparar Contadores
     */
    public boolean CompararContadores(String serialNro)
    {
    	boolean rep = false;
    	int ulfac = 0, ulnc = 0;
    	try{
            
            String Id_Maq = String.valueOf(this.Id_machine);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
        	Object[]record = (Object[]) new StaticSentence(this.sesion
                    , "SELECT ID_ULTFACTURA, ID_ULTINC FROM MAQUINA WHERE ID = '"+Id_Maq+"' AND SERIAL = ?"
                    , SerializerWriteString.INSTANCE
                    , new SerializerReadBasic(new Datas[] {Datas.STRING,Datas.STRING})).find(serialNro);
        	
        	
        	ulfac = Integer.valueOf(String.valueOf(record[0]));
        	ulnc = Integer.valueOf(String.valueOf(record[1]));
        	
        	if(ulfac == this.Ultima_FAC)
        	{
        		if(ulnc == this.Ultima_NC)
        		{
        			return true;
        		}
        		else
        		{
        			this.msnError = "El contador de Ultima Nota de Credito de la Máquina no coincide con el almacenado en el sistema.";
        			return false;
        		}
        	}else
        	{
        		if(ulnc == this.Ultima_NC)
        		{this.msnError = "El contador de Ultima Factura de la Máquina no coincide con el almacenado en el sistema.";}
        		else
        		{this.msnError = "El contador de Ultima Factura y Nota de Credito de la Máquina no coinciden con los almacenados en el sistema.";}
        		return false;
        	}
        //	serialReturn = (String) record[0];
        }catch(BasicException be)
        {
      	  this.msnError = be.getMessage();
      	 
        }
        
    	return rep;
    }
    /**
     * 
     * Obtengo el serial anterior al actual almacenado en BD
     * */
    public String DameUltimoSerial()
    {
        String serialReturn = null;
        int id = this.ConsultarMAX_ID();
        
        String Id_Maq = String.valueOf(id);
          
          while(Id_Maq.length()<2)
          {
              Id_Maq = "0" + Id_Maq;
          }
        
    	try{
    	Object[]record = (Object[]) new StaticSentence(this.sesion
                , "SELECT SERIAL FROM MAQUINA WHERE ID = ?"
                , SerializerWriteString.INSTANCE
                , new SerializerReadBasic(new Datas[] {Datas.STRING})).find(Id_Maq);
    	
        if(record != null)
        {serialReturn = (String) record[0];}
        
    }catch(BasicException be)
    {
  	  this.msnError = be.getMessage();
  	  return null;
    }
    	return serialReturn;
    }
    //Determinar si Esta inicializada la Memoria Fiscal de la IF
    public boolean isZ_X_cero()
    {
    	double monto = 0.0;
        //![0]
    	//monto = ReportePC.getAdditionalRate3Sale()+ ReportePC.getAdditionalRate3Tax();
    	//monto = monto + ReportePC.getAdditionalRateDevolution() + ReportePC.getAdditionalRateTaxDevolution();
//    	monto = monto + ReportePC.getFreeSalesTax() + ReportePC.getFreeTaxDevolution();
//    	monto = monto + ReportePC.getGeneralRate1Sale() + ReportePC.getGeneralRate1Tax();
//    	monto = monto + ReportePC.getGeneralRateDevolution() + ReportePC.getGeneralRateTaxDevolution();
//    	monto = monto + ReportePC.getReducedRate2Sale()  + ReportePC.getReducedRate2Tax();
//    	monto = monto + ReportePC.getReducedRateDevolution()  + ReportePC.getReducedRateTaxDevolution();
        //![0]    	
    	if(monto == 0.0)
    	{return true;}
    	else
    	{return false;}
    }
    // Determina si la Caja del sistema esta inicializada en 0.00 BsF
    public boolean isCajaSystem_Zero(String indCaja)
    {
    	try{
    		
    		 // Pagos
            Object[] valtickets = (Object []) new StaticSentence(this.sesion
                , "SELECT COUNT(*), SUM(PAYMENTS.TOTAL) " +
                  "FROM PAYMENTS, RECEIPTS " +
                  "WHERE PAYMENTS.RECEIPT = RECEIPTS.ID AND RECEIPTS.MONEY = ?"
                , SerializerWriteString.INSTANCE
                , new SerializerReadBasic(new Datas[] {Datas.INT, Datas.DOUBLE}))
                .find(indCaja);
            
            if(valtickets[1] != null)
            {   double montoTotal = (Double) valtickets[1];
            
            if(montoTotal == 0.0)
    		{return true;}
            else
            {return false;}
            }else
            {return true;}
    	
    	}catch (BasicException Be) {
			
           return false;
		}
    	
    	
    }
//M�todos Privados
    /*
     * Cargas Medios de Pagos
     */
    private void EstablecerMediosPagos()
    {
    	try
    	{
        String  sCmd = "PE01EFECTIVO      ";
     boolean rep =   this.FiscalPrinter.SendCmd(sCmd);
     
        sCmd = "PE02CESTA TICKET  ";
       rep &= this.FiscalPrinter.SendCmd(sCmd);
        sCmd = "PE05CHEQUE        ";
       rep &= this.FiscalPrinter.SendCmd(sCmd);
        sCmd = "PE09TARJT DEBITO  ";
      rep &=  this.FiscalPrinter.SendCmd(sCmd);
        sCmd = "PE13TARJT CREDITO ";
       rep &= this.FiscalPrinter.SendCmd(sCmd);
       
       
       
    	}catch(PrinterException pe)
        {
                this.msnError = pe.getMessage();
       	 }
    }
    /*
	 * Consultar el Maximo ID de la tabla Machine de la BD
	 * 
	 */
    private int ConsultarMAX_ID()
    {
    	int idMAX = 0;
    	
    	try{
    	Object[]record = (Object[]) new StaticSentence(this.sesion
                , "SELECT MAX(ID) FROM MAQUINA"
                , SerializerWriteString.INSTANCE
                , new SerializerReadBasic(new Datas[] {Datas.STRING})).find("");
    	
        if(record[0] != null)
        {idMAX = Integer.valueOf(record[0].toString());}
        
    	
    }catch(BasicException be)
    {
  	  this.msnError = be.getMessage();
  	  return 0;
    }
    	return idMAX;
    }
    
    private void DameContadores()
    {
    	
    	try
    	{
    		this.FiscalPrinter.getXReport();
    		if( ReportePC != null)
      	  {
    			this.Ultima_FAC = ReportePC.getNumberOfLastInvoice();
     		   this.Ultima_NC = ReportePC.getNumberOfLastCreditNote();
      	  }
    	}
    	catch(tfhka.PrinterException pex)
    	{
    		this.msnError = pex.getMessage();
    	}
    	
    	
    	
    }
    
    private void SubirData()
    { 
    	this.Id_machine = this.ConsultarMAX_ID();
        this.Tasas =  new  TaxInfo[4];
        
    	try{
    	S1Estado = this.FiscalPrinter.getS1PrinterData();
	      Thread.sleep(35);
        S3Estado = this.FiscalPrinter.getS3PrinterData();
    	      Thread.sleep(35);
    	ReportePC = this.FiscalPrinter.getXReport();
              Thread.sleep(35);
    	
    	  if(S1Estado != null && S3Estado != null && ReportePC != null)
    	  {
    		  
    		  this.Serial = S1Estado.getRegisteredMachineNumber();
    		  this.fecha = S1Estado.getCurrentPrinterDateTime();
    		  //Tasa0 � Execento
    		  this.Tasas[0] = new TaxInfo("000", "Tasa 0", "000", "000", "", 0.00, false, 0);
    		  this.Tasas[1] = new TaxInfo("001", "Tasa 1", "001", "001", "", S3Estado.getTax1()/100, false, 0);
    		  this.Tasas[2] = new TaxInfo("002", "Tasa 2", "002", "002", "", S3Estado.getTax2()/100, false, 0);
    		  this.Tasas[3] = new TaxInfo("003", "Tasa 3", "003", "003", "", S3Estado.getTax3()/100, false, 0);
    		 
    		  
    		  //Tasa1
    		 
    		   if ( S3Estado.getTypeTax1()!= 2)
    		   { this.Tasas[1].setCascade(true);
    		     this.Tasas[0].setCascade(true);
    		   }
    		   //Tasa2
    		  
    		   if ( S3Estado.getTypeTax2()!= 2)
    		   { this.Tasas[2].setCascade(true);}
    		   //Tasa3
    		   
    		   if ( S3Estado.getTypeTax3()!= 2)
    		   { this.Tasas[3].setCascade(true);}
    		   
    		   this.Ultima_FAC = S1Estado.getLastInvoiceNumber();
    		   this.Ultima_NC = ReportePC.getNumberOfLastCreditNote();
    	  }
    	}
    	catch(tfhka.PrinterException pex)
    	{
    		this.msnError = pex.getMessage();
    	}
    	catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    /*
     * Leo las tasas de la BD
     * 
     */
    // Listados para combo
    private  SentenceList getTaxList() {
        return new StaticSentence(this.sesion
            , "SELECT ID, NAME, CATEGORY, CUSTCATEGORY, PARENTID, RATE, RATECASCADE, RATEORDER FROM TAXES ORDER BY NAME"
            , null
            , new SerializerRead() { public Object readValues(DataRead dr) throws BasicException {
                return new TaxInfo(
                        dr.getString(1), 
                        dr.getString(2),
                        dr.getString(3),
                        dr.getString(4),
                        dr.getString(5),
                        dr.getDouble(6).doubleValue(),
                        dr.getBoolean(7).booleanValue(),
                        dr.getInt(8));
            }});
    }
}
