#include <linux/module.h>
#include <linux/init.h>
#include <linux/rfkill.h>
#include <linux/delay.h>
#include <linux/platform_device.h>
#include <mach/sys_config.h>
#include <linux/gpio.h>

static const char bt_name[] = "sunxi-bt";
static struct rfkill *sw_rfkill;
int    bt_used;
static int    bt_rst_n;
static int    ls_int;
static int    pcm_ch;
unsigned int bt_state = 0;

extern int sunxi_gpio_req(struct gpio_config *gpio);
extern int get_rf_mod_type(void);
extern char * get_rf_mod_name(void);
#define RF_MSG(...)     do {printk("[rfkill]: "__VA_ARGS__);} while(0)

static int rfkill_set_power(void *data, bool blocked)
{
  unsigned int mod_sel = get_rf_mod_type();
  
  RF_MSG("rfkill set power %d\n", !blocked);

  switch (mod_sel){
    case 2:  /* ap6210 */
    case 5:  /* rtl8723bs */
    case 7:  /* ap6476 */ 
    case 8:  /* ap6330 */
    case 9:  /* gb9663 */
      if (!blocked) {
        if(bt_rst_n != -1) 
          gpio_set_value(bt_rst_n, 1);
      } else {
        if(bt_rst_n != -1) 
          gpio_set_value(bt_rst_n, 0);
      }        
      break;
          
    default:
      RF_MSG("no bt module matched !!\n");
  }
  bt_state = !blocked;
  msleep(10);
  return 0;
}

static struct rfkill_ops sw_rfkill_ops = {
    .set_block = rfkill_set_power,
};

static int sw_rfkill_probe(struct platform_device *pdev)
{
    int ret = 0;

    sw_rfkill = rfkill_alloc(bt_name, &pdev->dev,
                        RFKILL_TYPE_BLUETOOTH, &sw_rfkill_ops, NULL);
    if (unlikely(!sw_rfkill))
        return -ENOMEM;

    rfkill_set_states(sw_rfkill, true, false);

    ret = rfkill_register(sw_rfkill);
    if (unlikely(ret)) {
        rfkill_destroy(sw_rfkill);
    }
    return ret;
}

static int sw_rfkill_remove(struct platform_device *pdev)
{
    if (likely(sw_rfkill)) {
        rfkill_unregister(sw_rfkill);
        rfkill_destroy(sw_rfkill);
    }
    return 0;
}

static struct platform_driver sw_rfkill_driver = {
    .probe = sw_rfkill_probe,
    .remove = sw_rfkill_remove,
    .driver = {
        .name = "sunxi-rfkill",
        .owner = THIS_MODULE,
    },
};

static struct platform_device sw_rfkill_dev = {
    .name = "sunxi-rfkill",
};

static int __init sw_rfkill_init(void)
{
	script_item_value_type_e type;
	script_item_u val;
	struct gpio_config  *gpio_p = NULL;

	type = script_get_item("bt_para", "bt_used", &val);
	if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
		RF_MSG("failed to fetch bt configuration!\n");
		return -1;
	}
	if (!val.val) {
		RF_MSG("init no bt used in configuration\n");
		return 0;
	}
	bt_used = val.val;
  
	bt_rst_n = -1;
	type = script_get_item("bt_para", "bt_rst_n", &val);
	if (SCIRPT_ITEM_VALUE_TYPE_PIO!=type)
		RF_MSG("mod has no bt_rst_n gpio\n");
	else {
		gpio_p = &val.gpio;
		bt_rst_n = gpio_p->gpio;
		sunxi_gpio_req(gpio_p);
	}	

	type = script_get_item("bt_para", "ls_int", &val);
	if (SCIRPT_ITEM_VALUE_TYPE_PIO!=type)
		RF_MSG("mod has no ls_int gpio\n");
	else{
		gpio_p = &val.gpio;
		ls_int = gpio_p->gpio;
		sunxi_gpio_req(gpio_p);
		__gpio_set_value(ls_int, 1);
		printk("gpio ls_int set val 1, act val %d\n", __gpio_get_value(ls_int));
	}
	
	type = script_get_item("bt_para", "pcm_ch", &val);
	if (SCIRPT_ITEM_VALUE_TYPE_PIO!=type)
		RF_MSG("mod has no pcm_ch gpio\n");
	else{
		gpio_p = &val.gpio;
		pcm_ch = gpio_p->gpio;
		sunxi_gpio_req(gpio_p);
		__gpio_set_value(pcm_ch, 1);
		printk("gpio pcm_ch set val 1, act val %d\n", __gpio_get_value(pcm_ch));
	}
	
	platform_device_register(&sw_rfkill_dev);
	return platform_driver_register(&sw_rfkill_driver);
}

static void __exit sw_rfkill_exit(void)
{
	if (!bt_used) {
		RF_MSG("exit no bt used in configuration");
		return ;
	}

	platform_driver_unregister(&sw_rfkill_driver);
	platform_device_unregister(&sw_rfkill_dev);
}

module_init(sw_rfkill_init);
module_exit(sw_rfkill_exit);

MODULE_DESCRIPTION("sunxi-rfkill driver");
MODULE_AUTHOR("Aaron.magic<mgaic@reuuimllatech.com>");
MODULE_LICENSE(GPL);

