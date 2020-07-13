#include "LW101MFN4_MIPI_RGB.h"

static void LCD_power_on(u32 sel);
static void LCD_power_off(u32 sel);
static void LCD_bl_open(u32 sel);
static void LCD_bl_close(u32 sel);

static void LCD_panel_init(u32 sel);
static void LCD_panel_exit(u32 sel);

#define panel_reset(val) sunxi_lcd_gpio_set_value(sel, 0, val)
#define power_en(val)  sunxi_lcd_gpio_set_value(sel, 1, val)

#define REGFLAG_END_OF_TABLE     0x102
#define REGFLAG_DELAY            0x101

struct lcd_setting_table {
    u16 cmd;
    u32 count;
    u8 para_list[64];
};

#if 1
static struct lcd_setting_table lcm_initialization_setting[] = {
	//JD9365 initial code
	
	//Page0
	{0xE0,01,{0x00}},
	
	//--- PASSWORD	----//
	{0xE1,01,{0x93}},
	{0xE2,01,{0x65}},
	{0xE3,01,{0xF8}},
	{0x80,01,{0x03}},//02=3lane,03=4lane //page 0
	
	
	
	//--- Page1  ----//
	{0xE0,01,{0x01}},
	
	//Set VCOM
	{0x00,01,{0x00}},
	{0x01,01,{0x6A}},
	{0x03,01,{0x00}},
	{0x04,01,{0x6C}},
	
	//VCSW2 control
	//{0x0C,01,{0x74}},
	
	//Set Gamma Power, VGMP,VGMN,VGSP,VGSN
	{0x17,01,{0x00}},
	{0x18,01,{0xD7}},//4.5V, D7=4.8V
	{0x19,01,{0x01}},//0.0V
	{0x1A,01,{0x00}},
	{0x1B,01,{0xD7}},
	{0x1C,01,{0x01}},
					
	//Set Gate Power
	{0x1F,01,{0x70}},	//VGH_REG=16.2V
	{0x20,01,{0x2D}},	//VGL_REG=-12V
	{0x21,01,{0x2D}},	//VGL_REG2=-12V
	{0x22,01,{0x7E}},	
	
	
	{0x35,01,{0x28}},	//SAP
	
	{0x37,01,{0x19}},	//SS=1,BGR=1
	
	//SET RGBCYC
	{0x38,01,{0x05}},	//JDT=101 zigzag inversion
	{0x39,01,{0x00}},
	{0x3A,01,{0x01}},
	{0x3C,01,{0x7C}},	//SET EQ3 for TE_H
	{0x3D,01,{0xFF}},	//SET CHGEN_ON, modify 20140806 
	{0x3E,01,{0xFF}},	//SET CHGEN_OFF, modify 20140806 
	{0x3F,01,{0x7F}},	//SET CHGEN_OFF2, modify 20140806
	
	
	//Set TCON
	{0x40,01,{0x06}},	//RSO=
	{0x41,01,{0xA0}},	//LN=640->1280 line
	{0x43,01,{0x1E}},	//VFP=30
	{0x44,01,{0x0B}},	//VBP=12
	{0x45,01,{0x28}},  //HBP=40
	
	//--- power voltage  ----//
	{0x55,01,{0x01}},	//DCDCM=1111 ////55h=0F=>3 power //55h=01=>JD/FP7721
	//{0x56,01,{0x01}},
	{0x57,01,{0xA9}},  //\u52a0\u5927VCL\u500d\u7387
	//{0x58,01,{0x0A}},
	{0x59,01,{0x0A}},	//VCL = -2.5V
	{0x5A,01,{0x2E}},	//VGH = 16.2V
	{0x5B,01,{0x1A}},	//VGL = -12V
	{0x5C,01,{0x15}},	//pump clk
	
	
	//--- Gamma  ----//
	{0x5D,01,{0x7F}},
	{0x5E,01,{0x57}},
	{0x5F,01,{0x43}},
	{0x60,01,{0x36}},
	{0x61,01,{0x31}},
	{0x62,01,{0x22}},
	{0x63,01,{0x27}},
	{0x64,01,{0x11}},
	{0x65,01,{0x2A}},
	{0x66,01,{0x2A}},
	{0x67,01,{0x2A}},
	{0x68,01,{0x49}},
	{0x69,01,{0x38}},
	{0x6A,01,{0x40}},
	{0x6B,01,{0x32}},
	{0x6C,01,{0x2E}},
	{0x6D,01,{0x21}},
	{0x6E,01,{0x10}},
	{0x6F,01,{0x02}},
	{0x70,01,{0x7F}},
	{0x71,01,{0x57}},
	{0x72,01,{0x43}},
	{0x73,01,{0x36}},
	{0x74,01,{0x31}},
	{0x75,01,{0x22}},
	{0x76,01,{0x27}},
	{0x77,01,{0x11}},
	{0x78,01,{0x2A}},
	{0x79,01,{0x2A}},
	{0x7A,01,{0x2A}},
	{0x7B,01,{0x49}},
	{0x7C,01,{0x38}},
	{0x7D,01,{0x40}},
	{0x7E,01,{0x32}},
	{0x7F,01,{0x2E}},
	{0x80,01,{0x21}},
	{0x81,01,{0x10}},
	{0x82,01,{0x02}},
	
	
	//Page2, for GIP
	{0xE0,01,{0x02}},
	
	//GIP_L Pin mapping
	{0x00,01,{0x52}},//RESET_EVEN
	{0x01,01,{0x55}},//VSSG_EVEN 
	{0x02,01,{0x55}},//VSSG_EVEN 
	{0x03,01,{0x50}},//STV2_ODD  
	{0x04,01,{0x77}},//VDD2_ODD  
	{0x05,01,{0x57}},//VDD1_ODD  
	{0x06,01,{0x55}},//x		 
	{0x07,01,{0x4E}},//CK11 	 
	{0x08,01,{0x4C}},//CK9		 
	{0x09,01,{0x5F}},//x		 
	{0x0A,01,{0x4A}},//CK7		 
	{0x0B,01,{0x48}},//CK5		 
	{0x0C,01,{0x55}},//x		 
	{0x0D,01,{0x46}},//CK3		 
	{0x0E,01,{0x44}},//CK1		 
	{0x0F,01,{0x40}},//STV1_ODD  
	{0x10,01,{0x55}},//x		 
	{0x11,01,{0x55}},//x		 
	{0x12,01,{0x55}},//x		 
	{0x13,01,{0x55}},//x		 
	{0x14,01,{0x55}},//x		 
	{0x15,01,{0x55}},//x		 
	
	//GIP_R Pin mapping
	{0x16,01,{0x53}},//RESET__EVEN
	{0x17,01,{0x55}},//VSSG_EVEN  
	{0x18,01,{0x55}},//VSSG_EVEN  
	{0x19,01,{0x51}},//STV2_EVEN  
	{0x1A,01,{0x77}},//VDD2_EVEN  
	{0x1B,01,{0x57}},//VDD1_EVEN  
	{0x1C,01,{0x55}},//x		  
	{0x1D,01,{0x4F}},//CK12 	  
	{0x1E,01,{0x4D}},//CK10 	  
	{0x1F,01,{0x5F}},//x		  
	{0x20,01,{0x4B}},//CK8		  
	{0x21,01,{0x49}},//CK6		  
	{0x22,01,{0x55}},//x		  
	{0x23,01,{0x47}},//CK4		  
	{0x24,01,{0x45}},//CK2		  
	{0x25,01,{0x41}},//STV1_EVEN  
	{0x26,01,{0x55}},//x		  
	{0x27,01,{0x55}},//x		  
	{0x28,01,{0x55}},//x		  
	{0x29,01,{0x55}},//x		  
	{0x2A,01,{0x55}},//x		  
	{0x2B,01,{0x55}},//x		  
						  
	//GIP_L_GS Pin mapping
	{0x2C,01,{0x13}},//RESET_EVEN	  
	{0x2D,01,{0x15}},//VSSG_EVEN		 
	{0x2E,01,{0x15}},//VSSG_EVEN	   
	{0x2F,01,{0x01}},//STV2_ODD 	   
	{0x30,01,{0x37}},//VDD2_ODD 	   
	{0x31,01,{0x17}},//VDD1_ODD 	   
	{0x32,01,{0x15}},//x			   
	{0x33,01,{0x0D}},//CK11 		   
	{0x34,01,{0x0F}},//CK9			   
	{0x35,01,{0x15}},//x			   
	{0x36,01,{0x05}},//CK7			   
	{0x37,01,{0x07}},//CK5			   
	{0x38,01,{0x15}},//x			   
	{0x39,01,{0x09}},//CK3			   
	{0x3A,01,{0x0B}},//CK1			   
	{0x3B,01,{0x11}},//STV1_ODD 	   
	{0x3C,01,{0x15}},//x			   
	{0x3D,01,{0x15}},//x			   
	{0x3E,01,{0x15}},//x			   
	{0x3F,01,{0x15}},//x			   
	{0x40,01,{0x15}},//x			   
	{0x41,01,{0x15}},//x			  
										   
	//GIP_R_GS Pin mapping				   
	{0x42,01,{0x12}},//RESET__EVEN	  
	{0x43,01,{0x15}},//VSSG_EVEN		 
	{0x44,01,{0x15}},//VSSG_EVEN	   
	{0x45,01,{0x00}},//STV2_EVEN	   
	{0x46,01,{0x37}},//VDD2_EVEN	   
	{0x47,01,{0x17}},//VDD1_EVEN	   
	{0x48,01,{0x15}},//x			   
	{0x49,01,{0x0C}},//CK12 		   
	{0x4A,01,{0x0E}},//CK10 		   
	{0x4B,01,{0x15}},//x			   
	{0x4C,01,{0x04}},//CK8			   
	{0x4D,01,{0x06}},//CK6			   
	{0x4E,01,{0x15}},//x			   
	{0x4F,01,{0x08}},//CK4			   
	{0x50,01,{0x0A}},//CK2				
	{0x51,01,{0x10}},//STV1_EVEN	   
	{0x52,01,{0x15}},//x			   
	{0x53,01,{0x15}},//x			   
	{0x54,01,{0x15}},//x			   
	{0x55,01,{0x15}},//x			   
	{0x56,01,{0x15}},//x			   
	{0x57,01,{0x15}},//x			   
	
	//GIP Timing  
	{0x58,01,{0x40}}, 
	//{0x59,01,{0x00}}, 
	//{0x5A,01,{0x00}}, 
	{0x5B,01,{0x10}}, 
	{0x5C,01,{0x06}},//STV_S0 
	{0x5D,01,{0x40}}, 
	{0x5E,01,{0x00}}, 
	{0x5F,01,{0x00}}, 
	{0x60,01,{0x40}},//ETV_W 
	{0x61,01,{0x03}}, 
	{0x62,01,{0x04}}, 
	{0x63,01,{0x6C}},//CKV_ON 
	{0x64,01,{0x6C}},//CKV_OFF 
	{0x65,01,{0x75}}, 
	{0x66,01,{0x08}},//ETV_S0 
	{0x67,01,{0xB4}}, //ckv_num/ckv_w
	{0x68,01,{0x08}}, //CKV_S0
	{0x69,01,{0x6C}},//CKV_ON
	{0x6A,01,{0x6C}},//CKV_OFF 
	{0x6B,01,{0x0C}}, //dummy
	//{0x6C,01,{0x00}},//GEQ_LINE 
	{0x6D,01,{0x00}},//GGND1 
	{0x6E,01,{0x00}},//GGND2 
	{0x6F,01,{0x88}}, 
	//{0x70,01,{0x00}}, 
	//{0x71,01,{0x00}}, 
	//{0x72,01,{0x06}}, 
	//{0x73,01,{0x7B}}, 
	//{0x74,01,{0x00}}, 
	{0x75,01,{0xBB}},//FLM_EN 
	{0x76,01,{0x00}}, 
	{0x77,01,{0x05}}, 
	{0x78,01,{0x2A}},//FLM_OFF 
	//{0x79,01,{0x00}}, 
	//{0x7A,01,{0x00}}, 
	//{0x7B,01,{0x00}}, 
	//{0x7C,01,{0x00}}, 
	//{0x7D,01,{0x03}}, 
	//{0x7E,01,{0x7B}}, 
	
	
	
	//Page4
	{0xE0,01,{0x04}},
	{0x09,01,{0x11}},
	{0x0E,01,{0x48}},	//Source EQ option
	{0x2B,01,{0x2B}},
	{0x2D,01,{0x03}},//defult 0x01
	{0x2E,01,{0x44}},
	
	//Page5
	{0xE0,01,{0x05}},
	{0x12,01,{0x72}},//VCI GAS detect voltage
	
	//Page0
	{0xE0,01,{0x00}},
	{0xE6,01,{0x02}},//WD_Timer
	{0xE7,01,{0x0C}},//WD_Timer
	
	//SLP OUT
	{0x11,01,{0x00}},		// SLPOUT
	{REGFLAG_DELAY,20,{}},
	
	//DISP ON
	{0x29,01,{0x00}},		// DSPON
	{REGFLAG_DELAY,200,{}},

	{REGFLAG_END_OF_TABLE, 0x00, {}},
	
	//--- TE----//
	//{0x35,01,{0x00}},
};
#else
static struct lcd_setting_table lcm_initialization_setting[] = {
	
	//JD9365 initial code

	//Page0
	{0xE0,01,{0x00}},

	//--- PASSWORD  ----//
	{0xE1,01,{0x93}},
	{0xE2,01,{0x65}},
	{0xE3,01,{0xF8}},
	{0x80,01,{0x03}},

	//Page4
	{0xE0,01,{0x04}},
	{0x2D,01,{0x03}},//lansel select by internal reg

	{0xE0,01,{0x00}},

	{0x70,01,{0x10}},	//DC0,DC1
	{0x71,01,{0x13}},	//DC2,DC3
	{0x72,01,{0x06}},	//DC7

	//--- Page1  ----//
	{0xE0,01,{0x01}},

	//Set VCOM
	{0x00,01,{0x00}},
	{0x01,01,{0x80}},
	{0x03,01,{0x00}},
	{0x04,01,{0x7B}},

	//Set Gamma Power,VGMP,VGMN,VGSP,VGSN
	{0x17,01,{0x00}},
	{0x18,01,{0xC2}},
	{0x19,01,{0x01}},
	{0x1A,01,{0x00}},
	{0x1B,01,{0xC2}},
	{0x1C,01,{0x01}},
	{0x0C,01,{0x74}},

	//Set Gate Power
	{0x1F,01,{0x3F}},
	{0x20,01,{0x24}},	
	{0x21,01,{0x24}},
	{0x22,01,{0x0E}},
	
	//SET RGBCYC
	{0x37,01,{0x09}},	//[5:4]ENZ[1:0]=10,[3]SS=1,[0]BGR=1
	{0x38,01,{0x04}},	//JDT=100 column inversion
	{0x39,01,{0x00}},	//RGB_N_EQ1,modify 20140806
	{0x3A,01,{0x01}},	//RGB_N_EQ2,modify 20140806
	{0x3C,01,{0x78}},	//SET EQ3 for TE_H
	{0x3D,01,{0xFF}},	//SET CHGEN_ON,modify 20140827 
	{0x3E,01,{0xFF}},	//SET CHGEN_OFF,modify 20140827 
	{0x3F,01,{0xFF}},	//SET CHGEN_OFF2,modify 20140827

	//Set TCON
	{0x40,01,{0x06}},	//RSO=800 Pixels
	{0x41,01,{0xA0}},	//LN=640->1280 line
	{0x43,01,{0x08}},	//VFP
	{0x44,01,{0x07}},	//VBP
	{0x45,01,{0x24}},	//HBP

	//--- power voltage  ----//
	{0x55,01,{0xFF}},	//DCDCM=1111,External pwoer ic
	{0x56,01,{0x01}},
	{0x57,01,{0x69}},
	{0x58,01,{0x0A}},
	{0x59,01,{0x2A}},	//VCL = -2.9V
	{0x5A,01,{0x28}},	//VGH = 15V
	{0x5B,01,{0x0F}},	//VGL = -10V

	//--- Gamma  ----//
	{0x5D,01,{0x7C}},
	{0x5E,01,{0x67}},
	{0x5F,01,{0x55}},
	{0x60,01,{0x48}},
	{0x61,01,{0x3E}},
	{0x62,01,{0x2D}},
	{0x63,01,{0x30}},
	{0x64,01,{0x16}},
	{0x65,01,{0x2C}},
	{0x66,01,{0x28}},
	{0x67,01,{0x26}},
	{0x68,01,{0x42}},
	{0x69,01,{0x30}},
	{0x6A,01,{0x38}},
	{0x6B,01,{0x29}},
	{0x6C,01,{0x26}},
	{0x6D,01,{0x1C}},
	{0x6E,01,{0x0D}},
	{0x6F,01,{0x00}},
	{0x70,01,{0x7C}},
	{0x71,01,{0x69}},
	{0x72,01,{0x55}},
	{0x73,01,{0x48}},
	{0x74,01,{0x3E}},
	{0x75,01,{0x2D}},
	{0x76,01,{0x30}},
	{0x77,01,{0x16}},
	{0x78,01,{0x2C}},
	{0x79,01,{0x28}},
	{0x7A,01,{0x26}},
	{0x7B,01,{0x42}},
	{0x7C,01,{0x30}},
	{0x7D,01,{0x38}},
	{0x7E,01,{0x29}},
	{0x7F,01,{0x26}},
	{0x80,01,{0x1C}},
	{0x81,01,{0x0D}},
	{0x82,01,{0x00}},

	//Page2,for GIP
	{0xE0,01,{0x02}},

	//GIP_L Pin mapping
	{0x00,01,{0x09}},//L1
	{0x01,01,{0x05}},//L2   
	{0x02,01,{0x08}},//L3
	{0x03,01,{0x04}},//L4
	{0x04,01,{0x06}},//L5
	{0x05,01,{0x0A}},//L6
	{0x06,01,{0x07}},//L7
	{0x07,01,{0x0B}},//L8
	{0x08,01,{0x00}},//L9
	{0x09,01,{0x1F}},//L10
	{0x0A,01,{0x1F}},//L11
	{0x0B,01,{0x1F}},//L12
	{0x0C,01,{0x1F}},//L13
	{0x0D,01,{0x1F}},//L14
	{0x0E,01,{0x1F}},//L15
	{0x0F,01,{0x17}},//L16
	{0x10,01,{0x37}},//L17
	{0x11,01,{0x1F}},//L18
	{0x12,01,{0x1F}},//L19
	{0x13,01,{0x1E}},//L20
	{0x14,01,{0x10}},//L21
	{0x15,01,{0x1F}},//L22

	//GIP_R Pin mapping
	{0x16,01,{0x09}},
	{0x17,01,{0x05}},
	{0x18,01,{0x08}},
	{0x19,01,{0x04}},
	{0x1A,01,{0x06}},
	{0x1B,01,{0x0A}},
	{0x1C,01,{0x07}},
	{0x1D,01,{0x0B}},
	{0x1E,01,{0x00}},
	{0x1F,01,{0x1F}},
	{0x20,01,{0x1F}},
	{0x21,01,{0x1F}},
	{0x22,01,{0x1F}},
	{0x23,01,{0x1F}},
	{0x24,01,{0x1F}},
	{0x25,01,{0x17}},
	{0x26,01,{0x37}},
	{0x27,01,{0x1F}},
	{0x28,01,{0x1F}},
	{0x29,01,{0x1E}},
	{0x2A,01,{0x10}},
	{0x2B,01,{0x1F}},

	//GIP_L_GS Pin mapping
	{0x2C,01,{0x06}},//L1
	{0x2D,01,{0x0A}},//L2   
	{0x2E,01,{0x07}},//L3
	{0x2F,01,{0x0B}},//L4
	{0x30,01,{0x09}},//L5
	{0x31,01,{0x05}},//L6
	{0x32,01,{0x08}},//L7
	{0x33,01,{0x04}},//L8
	{0x34,01,{0x10}},//L9
	{0x35,01,{0x1F}},//L10
	{0x36,01,{0x1F}},//L11
	{0x37,01,{0x1F}},//L12
	{0x38,01,{0x1F}},//L13
	{0x39,01,{0x1F}},//L14
	{0x3A,01,{0x1F}},//L15
	{0x3B,01,{0x17}},//L16
	{0x3C,01,{0x37}},//L17
	{0x3D,01,{0x1F}},//L18
	{0x3E,01,{0x1E}},//L19
	{0x3F,01,{0x1F}},//L20
	{0x40,01,{0x00}},//L21
	{0x41,01,{0x1F}},//L22

	//GIP_R_GS Pin mapping
	{0x42,01,{0x06}},
	{0x43,01,{0x0A}},
	{0x44,01,{0x07}},
	{0x45,01,{0x0B}},
	{0x46,01,{0x09}},
	{0x47,01,{0x05}},
	{0x48,01,{0x08}},
	{0x49,01,{0x04}},
	{0x4A,01,{0x10}},
	{0x4B,01,{0x1F}},
	{0x4C,01,{0x1F}},
	{0x4D,01,{0x1F}},
	{0x4E,01,{0x1F}},
	{0x4F,01,{0x1F}},
	{0x50,01,{0x1F}},
	{0x51,01,{0x17}},
	{0x52,01,{0x37}},
	{0x53,01,{0x1F}},
	{0x54,01,{0x1E}},
	{0x55,01,{0x1F}},
	{0x56,01,{0x00}},
	{0x57,01,{0x1F}},

	//GIP Timing  
	{0x58,01,{0x01}},//RD 58h= //0x01                
	{0x59,01,{0x00}},//RD 59h= //0x00                
	{0x5A,01,{0x00}},//RD 5Ah= //0x00                
	{0x5B,01,{0x00}},//RD 5Bh= //0x00                
	{0x5C,01,{0x01}},//RD 5Ch= //0x01                
	{0x5D,01,{0x70}},//RD 5Dh= //0x30                
	{0x5E,01,{0x00}},//RD 5Eh= //0x00                
	{0x5F,01,{0x00}},//RD 5Fh= //0x00                
	{0x60,01,{0x40}},//RD 60h= //0x30                
	{0x61,01,{0x00}},//RD 61h= //0x00                
	{0x62,01,{0x00}},//RD 62h= //0x00                
	{0x63,01,{0x65}},//RD 63h= //0x03                //STV_ON
	{0x64,01,{0x65}},//RD 64h= //0x6A                //STV_OFF
	{0x65,01,{0x45}},//RD 65h= //0x45                
	{0x66,01,{0x09}},//RD 66h= //0x08                
	{0x67,01,{0x73}},//RD 67h= //0x73                
	{0x68,01,{0x05}},//RD 68h= //0x05                
	{0x69,01,{0x00}},//RD 69h= //0x06                //CKV_ON
	{0x6A,01,{0x64}},//RD 6Ah= //0x6A                //CKV_OFF
	{0x6B,01,{0x00}},//RD 6Bh= //0x08                
	{0x6C,01,{0x00}},//RD 6Ch= //                
	{0x6D,01,{0x00}},//RD 6Dh= // 
	{0x6E,01,{0x00}},//RD 6Eh= // 
	{0x6F,01,{0x88}},//RD 6Fh= // 
	{0x70,01,{0x00}},//RD 70h= // 
	{0x71,01,{0x00}},//RD 71h= // 
	{0x72,01,{0x06}},//RD 72h= // 
	{0x73,01,{0x7B}},//RD 73h= // 
	{0x74,01,{0x00}},//RD 74h= // 
	{0x75,01,{0x80}},//RD 75h= // 
	{0x76,01,{0x00}},//RD 76h= // 
	{0x77,01,{0x05}},//RD 77h= // 
	{0x78,01,{0x18}},//RD 78h= //0x10 
	{0x79,01,{0x00}},//RD 79h= // 
	{0x7A,01,{0x00}},//RD 7Ah= // 
	{0x7B,01,{0x00}},//RD 7Bh= // 
	{0x7C,01,{0x00}},//RD 7Ch= // 
	{0x7D,01,{0x03}},//RD 7Dh= // 
	{0x7E,01,{0x7B}},//RD 7Eh= // 

	// Add ESD Protect
	//Page4
	{0xE0,01,{0x04}},
	{0x09,01,{0x10}},
	{0x2B,01,{0x2B}},
	{0x2E,01,{0x44}},

	//Page0
	{0xE0,01,{0x00}},
	{0xE6,01,{0x02}},
	{0xE7,01,{0x02}},

	//SLP OUT
	{0x11,01,{0x00}},  	// SLPOUT
	{REGFLAG_DELAY,20,{}}, 


	//DISP ON
	{0x29,01,{0x00}},  	// DSPON
	{REGFLAG_DELAY,200,{}},

	{REGFLAG_END_OF_TABLE, 0x00, {}}, 

	//--- TE----//
	//{0x35,01,{0x00}},
};
#endif

static void LCD_cfg_panel_info(panel_extend_para * info)
{
	u32 i = 0, j=0;
	u32 items;
	u8 lcd_gamma_tbl[][2] =
	{
		//{input value, corrected value}
		{0, 0},
		{15, 15},
		{30, 30},
		{45, 45},
		{60, 60},
		{75, 75},
		{90, 90},
		{105, 105},
		{120, 120},
		{135, 135},
		{150, 150},
		{165, 165},
		{180, 180},
		{195, 195},
		{210, 210},
		{225, 225},
		{240, 240},
		{255, 255},
	};

	u32 lcd_cmap_tbl[2][3][4] = {
	{
		{LCD_CMAP_G0,LCD_CMAP_B1,LCD_CMAP_G2,LCD_CMAP_B3},
		{LCD_CMAP_B0,LCD_CMAP_R1,LCD_CMAP_B2,LCD_CMAP_R3},
		{LCD_CMAP_R0,LCD_CMAP_G1,LCD_CMAP_R2,LCD_CMAP_G3},
		},
		{
		{LCD_CMAP_B3,LCD_CMAP_G2,LCD_CMAP_B1,LCD_CMAP_G0},
		{LCD_CMAP_R3,LCD_CMAP_B2,LCD_CMAP_R1,LCD_CMAP_B0},
		{LCD_CMAP_G3,LCD_CMAP_R2,LCD_CMAP_G1,LCD_CMAP_R0},
		},
	};

	items = sizeof(lcd_gamma_tbl)/2;
	for (i=0; i<items-1; i++) {
		u32 num = lcd_gamma_tbl[i+1][0] - lcd_gamma_tbl[i][0];

		for (j=0; j<num; j++) {
			u32 value = 0;

			value = lcd_gamma_tbl[i][1] + ((lcd_gamma_tbl[i+1][1] - lcd_gamma_tbl[i][1]) * j)/num;
			info->lcd_gamma_tbl[lcd_gamma_tbl[i][0] + j] = (value<<16) + (value<<8) + value;
		}
	}
	info->lcd_gamma_tbl[255] = (lcd_gamma_tbl[items-1][1]<<16) + (lcd_gamma_tbl[items-1][1]<<8) + lcd_gamma_tbl[items-1][1];

	memcpy(info->lcd_cmap_tbl, lcd_cmap_tbl, sizeof(lcd_cmap_tbl));

}

static s32 LCD_open_flow(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_open_flow\n");
	
	LCD_OPEN_FUNC(sel, LCD_power_on, 20);   //open lcd power, and delay 50ms
	LCD_OPEN_FUNC(sel, LCD_panel_init, 10);   //open lcd power, than delay 200ms
	LCD_OPEN_FUNC(sel, sunxi_lcd_tcon_enable, 20);     //open lcd controller, and delay 100ms
	LCD_OPEN_FUNC(sel, LCD_bl_open, 0);     //open lcd backlight, and delay 0ms

	return 0;
}

static s32 LCD_close_flow(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_close_flow\n");
	LCD_CLOSE_FUNC(sel, LCD_bl_close, 0);       //close lcd backlight, and delay 0ms
	LCD_CLOSE_FUNC(sel, sunxi_lcd_tcon_disable, 0);         //close lcd controller, and delay 0ms
	LCD_CLOSE_FUNC(sel, LCD_panel_exit,	200);   //open lcd power, than delay 200ms
	LCD_CLOSE_FUNC(sel, LCD_power_off, 500);   //close lcd power, and delay 500ms

	return 0;
}

static void LCD_power_on(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_power_on\n");
	sunxi_lcd_power_enable(sel, 0);//config lcd_power pin to open lcd power
	sunxi_lcd_delay_ms(5);
	sunxi_lcd_power_enable(sel, 1);//config lcd_power pin to open lcd power0
	sunxi_lcd_delay_ms(5);
	sunxi_lcd_power_enable(sel, 2);//config lcd_power pin to open lcd power2
	sunxi_lcd_delay_ms(5);

	power_en(1);
	sunxi_lcd_delay_ms(50);
	panel_reset(1);
	sunxi_lcd_delay_ms(50);
	panel_reset(0);
	sunxi_lcd_delay_ms(100);
	panel_reset(1);
	sunxi_lcd_delay_ms(200);
	
	sunxi_lcd_pin_cfg(sel, 1);
}

static void LCD_power_off(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_power_off\n");
	sunxi_lcd_pin_cfg(sel, 0);
	power_en(0);
	sunxi_lcd_delay_ms(20);
	panel_reset(0);
	sunxi_lcd_delay_ms(5);
	sunxi_lcd_power_disable(sel, 2);//config lcd_power pin to close lcd power2
	sunxi_lcd_delay_ms(5);
	sunxi_lcd_power_disable(sel, 1);//config lcd_power pin to close lcd power1
	sunxi_lcd_delay_ms(5);
	sunxi_lcd_power_disable(sel, 0);//config lcd_power pin to close lcd power
}

static void LCD_bl_open(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_bl_open\n");
	sunxi_lcd_pwm_enable(sel);
	sunxi_lcd_backlight_enable(sel);//config lcd_bl_en pin to open lcd backlight
}

static void LCD_bl_close(u32 sel)
{
	pr_info("[LW101MFN4_MIPI_RGB]LCD_bl_close\n");
	sunxi_lcd_backlight_disable(sel);//config lcd_bl_en pin to close lcd backlight
	sunxi_lcd_pwm_disable(sel);
}

static void LCD_panel_init(u32 sel)
{
	u32 i;

	pr_info("[LW101MFN4_MIPI_RGB]LCD_panel_init\n");
	
	for (i = 0; ; i++) {
		if(lcm_initialization_setting[i].cmd == REGFLAG_END_OF_TABLE) {
			break;
		} 
		else if (lcm_initialization_setting[i].cmd == REGFLAG_DELAY) {
			sunxi_lcd_delay_ms(lcm_initialization_setting[i].count);
		} else {
			dsi_dcs_wr(sel, (u8)lcm_initialization_setting[i].cmd, lcm_initialization_setting[i].para_list, lcm_initialization_setting[i].count);
		}
	}

	sunxi_lcd_dsi_clk_enable(sel);

	return;
}

static void LCD_panel_exit(u32 sel)
{
	sunxi_lcd_dsi_dcs_write_0para(sel,DSI_DCS_SET_DISPLAY_OFF);
	sunxi_lcd_delay_ms(20);
	//sunxi_lcd_dsi_dcs_write_0para(sel,DSI_DCS_ENTER_SLEEP_MODE);
	//sunxi_lcd_delay_ms(80);
	
	sunxi_lcd_dsi_clk_disable(sel);

	return ;
}

//sel: 0:lcd0; 1:lcd1
static s32 LCD_user_defined_func(u32 sel, u32 para1, u32 para2, u32 para3)
{
	return 0;
}

__lcd_panel_t LW101MFN4_MIPI_RGB_panel = {
	/* panel driver name, must mach the name of lcd_drv_name in sys_config.fex */
	.name = "LW101MFN4_MIPI_RGB",
	.func = {
		.cfg_panel_info = LCD_cfg_panel_info,
		.cfg_open_flow = LCD_open_flow,
		.cfg_close_flow = LCD_close_flow,
		.lcd_user_defined_func = LCD_user_defined_func,
	},
};
