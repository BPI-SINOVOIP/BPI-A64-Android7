#include "axp-rw.h"
#include <linux/mfd/axp-mfd-152.h>

static uint8_t axp_reg_addr = 0;

static void axp152_mfd_irq_work(struct work_struct *work)
{
	struct axp_dev *chip =
		container_of(work, struct axp_dev, irq_work);
	uint64_t irqs = 0;
	while (1) {
		if (chip->ops->read_irqs(chip, &irqs)){
			break;
		}

		irqs &= chip->irqs_enabled;
		if (irqs == 0){
			break;
		}

		if(irqs > 0xffffffff){
			blocking_notifier_call_chain(
					&chip->notifier_list, (irqs >>32), (void *)1);
		}
		else{
			blocking_notifier_call_chain(
					&chip->notifier_list, irqs, 0);
		}
	}
#ifdef	CONFIG_AXP_TWI_USED
	enable_irq(chip->client->irq);
#else
	arisc_enable_nmi_irq();
#endif

}

static int __devinit axp152_init_chip(struct axp_dev *chip)
{
	uint8_t chip_id;
	uint8_t dcdc2_ctl;
	uint8_t v[11] = {0x00,POWER15_INTEN2, 0x03,POWER15_INTEN3,0x00,
				POWER15_INTSTS1,0xff,POWER15_INTSTS2, 0xff,
				POWER15_INTSTS3,0xff,};
	int err;
	unsigned char devaddr = TWI_RTSADDR_AXP152;
	/*read chip id*/
	err =  __axp_read(&devaddr, chip->client, POWER15_IC_TYPE, &chip_id, false);
	if (err) {
		printk("[AXP15-MFD] try to read chip id failed!\n");
		return err;
	}
	INIT_WORK(&chip->irq_work, axp152_mfd_irq_work);
	/*enable irqs and clear*/
	err =  __axp_writes(&devaddr, chip->client, POWER15_INTEN1, 11, v, false);
	if (err) {
		printk("[AXP15-MFD] try to clear irq failed!\n");
		return err;
	}

	dev_info(chip->dev, "AXP (CHIP ID: 0x%02x) detected\n", chip_id);
	chip->type = AXP152;

	/* mask and clear all IRQs */
	chip->irqs_enabled = 0xffffff;
	chip->ops->disable_irqs(chip, chip->irqs_enabled);
#ifdef CONFIG_ARCH_SUN7I
	writel(0x00,NMI_CTL_REG);
	writel(0x01,NMI_IRG_PENDING_REG);
	writel(0x00,NMI_INT_ENABLE_REG);
#endif

    /* enable dcdc2 dvm */
	err =  __axp_read(&devaddr, chip->client, 0x25, &dcdc2_ctl, false);
	if(err){
		printk(KERN_ERR "[AXP15-MFD] try to read reg[25H] failed!\n");
		return err;
	}
	dcdc2_ctl |= (1<<2);
	err = __axp_write(&devaddr, chip->client, 0x25, dcdc2_ctl, false);
	if(err){
		printk(KERN_ERR "[AXP15-MFD] try to enable dcdc2 dvm failed!\n");
		return err;
	}

	printk("[AXP15-MFD] enable dcdc2 dvm.\n");

	return 0;
}

static int axp152_disable_irqs(struct axp_dev *chip, uint64_t irqs)
{
	uint8_t v[5];
	int ret;
	unsigned char devaddr = TWI_RTSADDR_AXP152;

	chip->irqs_enabled &= ~irqs;

	v[0] = ((chip->irqs_enabled) & 0xff);
	v[1] = POWER15_INTEN2;
	v[2] = ((chip->irqs_enabled) >> 8) & 0xff;
	v[3] = POWER15_INTEN3;
	v[4] = ((chip->irqs_enabled) >> 16) & 0xff;
	ret =  __axp_writes(&devaddr, chip->client, POWER15_INTEN1, 5, v, false);
#ifdef CONFIG_ARCH_SUN7I
	writel(0x0,NMI_INT_ENABLE_REG);
#endif
	return ret;

}

static int axp152_enable_irqs(struct axp_dev *chip, uint64_t irqs)
{
	uint8_t v[5];
	int ret;
	unsigned char devaddr = TWI_RTSADDR_AXP152;

	chip->irqs_enabled |=  irqs;

	v[0] = ((chip->irqs_enabled) & 0xff);
	v[1] = POWER15_INTEN2;
	v[2] = ((chip->irqs_enabled) >> 8) & 0xff;
	v[3] = POWER15_INTEN3;
	v[4] = ((chip->irqs_enabled) >> 16) & 0xff;
	ret =  __axp_writes(&devaddr, chip->client, POWER15_INTEN1, 5, v, false);
#ifdef CONFIG_ARCH_SUN7I
	writel(0x1,NMI_INT_ENABLE_REG);
#endif
	return ret;
}

static int axp152_read_irqs(struct axp_dev *chip, uint64_t *irqs)
{
	uint8_t v[3] = {0, 0, 0};
	int ret;
	unsigned char devaddr = TWI_RTSADDR_AXP152;
	ret =  __axp_reads(&devaddr, chip->client, POWER15_INTSTS1, 3, v, false);
#ifdef CONFIG_ARCH_SUN7I
	writel(0x01,NMI_IRG_PENDING_REG);
#endif
	if (ret < 0)
		return ret;

	*irqs =((((uint64_t) v[2])<< 16) | (((uint64_t)v[1]) << 8) | ((uint64_t) v[0]));
	return 0;
}


static ssize_t axp152_offvol_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
	uint8_t val = 0;
	axp_read(dev,POWER15_VOFF_SET,&val);
	return sprintf(buf,"%d\n",(val & 0x07) * 100 + 2600);
}

static ssize_t axp152_offvol_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if (tmp < 2600)
		tmp = 2600;
	if (tmp > 3300)
		tmp = 3300;

	axp_read(dev,POWER15_VOFF_SET,&val);
	val &= 0xf8;
	val |= ((tmp - 2600) / 100);
	axp_write(dev,POWER15_VOFF_SET,val);
	return count;
}

static ssize_t axp152_noedelay_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	axp_read(dev,POWER15_OFF_CTL,&val);
	if( (val & 0x03) == 0)
		return sprintf(buf,"%d\n",128);
	else
		return sprintf(buf,"%d\n",(val & 0x03) * 1000);
}

static ssize_t axp152_noedelay_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if (tmp < 1000)
		tmp = 128;
	if (tmp > 3000)
		tmp = 3000;
	axp_read(dev,POWER15_OFF_CTL,&val);
	val &= 0xfc;
	val |= ((tmp) / 1000);
	axp_write(dev,POWER15_OFF_CTL,val);
	return count;
}

static ssize_t axp152_pekopen_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	int tmp = 0;
	axp_read(dev,POWER15_PEK_SET,&val);
	switch(val >> 6){
		case 0: tmp = 128;break;
		case 1: tmp = 3000;break;
		case 2: tmp = 1000;break;
		case 3: tmp = 2000;break;
		default:
			tmp = 0;break;
	}
	return sprintf(buf,"%d\n",tmp);
}

static ssize_t axp152_pekopen_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	axp_read(dev,POWER15_PEK_SET,&val);
	if (tmp < 1000)
		val &= 0x3f;
	else if(tmp < 2000){
		val &= 0x3f;
		val |= 0x80;
	}
	else if(tmp < 3000){
		val &= 0x3f;
		val |= 0xc0;
	}
	else {
		val &= 0x3f;
		val |= 0x40;
	}
	axp_write(dev,POWER15_PEK_SET,val);
	return count;
}

static ssize_t axp152_peklong_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val = 0;
	axp_read(dev,POWER15_PEK_SET,&val);
	return sprintf(buf,"%d\n",((val >> 4) & 0x03) * 500 + 1000);
}

static ssize_t axp152_peklong_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if(tmp < 1000)
		tmp = 1000;
	if(tmp > 2500)
		tmp = 2500;
	axp_read(dev,POWER15_PEK_SET,&val);
	val &= 0xcf;
	val |= (((tmp - 1000) / 500) << 4);
	axp_write(dev,POWER15_PEK_SET,val);
	return count;
}

static ssize_t axp152_peken_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	axp_read(dev,POWER15_PEK_SET,&val);
	return sprintf(buf,"%d\n",((val >> 3) & 0x01));
}

static ssize_t axp152_peken_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if(tmp)
		tmp = 1;
	axp_read(dev,POWER15_PEK_SET,&val);
	val &= 0xf7;
	val |= (tmp << 3);
	axp_write(dev,POWER15_PEK_SET,val);
	return count;
}

static ssize_t axp152_pekdelay_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	axp_read(dev,POWER15_PEK_SET,&val);

	return sprintf(buf,"%d\n",((val >> 2) & 0x01)? 64:8);
}

static ssize_t axp152_pekdelay_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if(tmp <= 8)
		tmp = 0;
	else
		tmp = 1;
	axp_read(dev,POWER15_PEK_SET,&val);
	val &= 0xfb;
	val |= tmp << 2;
	axp_write(dev,POWER15_PEK_SET,val);
	return count;
}

static ssize_t axp152_pekclose_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	axp_read(dev,POWER15_PEK_SET,&val);
	return sprintf(buf,"%d\n",((val & 0x03) * 2000) + 4000);
}

static ssize_t axp152_pekclose_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if(tmp < 4000)
		tmp = 4000;
	if(tmp > 10000)
		tmp =10000;
	tmp = (tmp - 4000) / 2000 ;
	axp_read(dev,POWER15_PEK_SET,&val);
	val &= 0xfc;
	val |= tmp ;
	axp_write(dev,POWER15_PEK_SET,val);
	return count;
}

static ssize_t axp152_ovtemclsen_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
    uint8_t val;
	axp_read(dev,POWER15_HOTOVER_CTL,&val);
	return sprintf(buf,"%d\n",((val >> 2) & 0x01));
}

static ssize_t axp152_ovtemclsen_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 10);
	if(tmp)
		tmp = 1;
	axp_read(dev,POWER15_HOTOVER_CTL,&val);
	val &= 0xfb;
	val |= tmp << 2 ;
	axp_write(dev,POWER15_HOTOVER_CTL,val);
	return count;
}

static ssize_t axp152_reg_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
	uint8_t val;
	axp_read(dev,axp_reg_addr,&val);
	return sprintf(buf,"REG[%x]=%x\n",axp_reg_addr,val);
}

static ssize_t axp152_reg_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val;
	tmp = simple_strtoul(buf, NULL, 16);
	if( tmp < 256 )
		axp_reg_addr = tmp;
	else {
		val = tmp & 0x00FF;
		axp_reg_addr= (tmp >> 8) & 0x00FF;
		axp_write(dev,axp_reg_addr, val);
	}
	return count;
}

static ssize_t axp152_regs_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
	uint8_t val[2];
	axp_reads(dev,axp_reg_addr,2,val);
	return sprintf(buf,"REG[0x%x]=0x%x,REG[0x%x]=0x%x\n",axp_reg_addr,val[0],axp_reg_addr+1,val[1]);
}

static ssize_t axp152_regs_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	int tmp;
	uint8_t val[3];
	tmp = simple_strtoul(buf, NULL, 16);
	if( tmp < 256 )
		axp_reg_addr = tmp;
	else {
		axp_reg_addr= (tmp >> 16) & 0xFF;
		val[0] = (tmp >> 8) & 0xFF;
		val[1] = axp_reg_addr + 1;
		val[2] = tmp & 0xFF;
		axp_writes(dev,axp_reg_addr,3,val);
	}
	return count;
}

static struct device_attribute axp152_mfd_attrs[] = {
	AXP_MFD_ATTR(axp152_offvol),
	AXP_MFD_ATTR(axp152_noedelay),
	AXP_MFD_ATTR(axp152_pekopen),
	AXP_MFD_ATTR(axp152_peklong),
	AXP_MFD_ATTR(axp152_peken),
	AXP_MFD_ATTR(axp152_pekdelay),
	AXP_MFD_ATTR(axp152_pekclose),
	AXP_MFD_ATTR(axp152_ovtemclsen),
	AXP_MFD_ATTR(axp152_reg),
	AXP_MFD_ATTR(axp152_regs),
};

