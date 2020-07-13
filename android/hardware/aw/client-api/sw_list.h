/*
 * OpenVirtualization:
 * For additional details and support contact developer@sierraware.com.
 * Additional documentation can be found at www.openvirtualization.org
 *
 * Copyright (C) 2011 SierraWare
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * list declrations
 */

#ifndef __LIB_LIST_H__
#define __LIB_LIST_H__

#define LIST_POISON_PREV    0xDEADBEEF
#define LIST_POISON_NEXT    0xFADEBABE

/**
 * @brief
 */
struct list {
    struct list *next, *prev;
};

#define INIT_HEAD(__lname)  { &(__lname), &(__lname) }
#define LIST_HEAD(_lname)   struct list _lname = INIT_HEAD(_lname)
#define INIT_LIST_HEAD(ptr)  do { \
		(ptr)->next = ptr; (ptr)->prev = ptr;   \
	}while (0);

#define list_entry(ptr, type, member) \
	((type *)((char *)(ptr)-(unsigned long)(&((type *)0)->member)))

#define list_for_each(curr, head) \
	for (curr = (head)->next; curr != head; curr = (curr)->next)

#define list_for_each_entry(ptr, head ,member) \
	for(ptr = list_entry((head)->next, typeof(*ptr), member); \
		&ptr->member != (head); \
		ptr = list_entry(ptr->member.next , typeof(*ptr), member))\


/**
 * list_for_each_entry_safe - iterate over list of given type
 * safe against removal of list entry
 * @param pos:    the type * to use as a loop cursor.
 * @param n:      another type * to use as temporary storage
 * @param head:   the head for your list.
 * @param member: the name of the list_struct within the struct.
 */
#define list_for_each_entry_safe(pos, n, head, member)          \
	for (pos = list_entry((head)->next, typeof(*pos), member),  \
		n = list_entry(pos->member.next, typeof(*pos), member); \
			&pos->member != (head);                    \
		pos = n, n = list_entry(n->member.next, typeof(*n), member))

/**
 * @brief
 *
 * @param prev
 * @param next
 * @param new
 */
static inline void __list_add(struct list *prev,
                  struct list *next, struct list *n)
{
	n->prev = prev;
	n->next = next;
	prev->next = n;
	next->prev = n;
}

/**
 * @brief
 * Adds the new node after the given head.
 * @param head: List head after which the "new" node should be added.
 * @param new: New node that needs to be added to list.
 * @note Please note that new node is added after the head.
 */
static inline void list_add(struct list *head, struct list *n)
{
	__list_add(head, head->next, n);
}

/**
 * Adds a node at the tail where tnode points to tail node.
 * @param tnode: The current tail node.
 * @param new: The new node to be added before tail.
 * @note: Please note that new node is added before tail node.
 */
static inline void list_add_tail(struct list *tnode, struct list *n)
{
	__list_add(tnode->prev, tnode, n);
}

/**
 * @brief
 *
 * @param node
 * @param prev
 * @param next
 */
static inline void __list_del(struct list *node,
                  struct list *prev, struct list *next)
{
	prev->next = node->next;
	next->prev = node->prev;
	node->next = (struct list *)LIST_POISON_NEXT;
	node->prev = (struct list *)LIST_POISON_PREV;
}

/**
 * @brief
 * Deletes a given node from list.
 * @param node: Node to be deleted.
 *
 * @param node
 */
static inline void list_del(struct list *node)
{
	__list_del(node, node->prev, node->next);
}

/**
 * @brief
 *
 * @param head
 *
 * @return
 */
static inline struct list *list_pop_tail(struct list *head)
{
	struct list *dnode = head->prev;
	list_del(head->prev);
	return dnode;
}

/**
 * @brief
 *
 * @param head
 *
 * @return
 */
static inline struct list *list_pop(struct list *head)
{
	struct list *dnode = head->next;
	list_del(head->next);
	return dnode;
}

/**
 * @brief
 *
 * @param head
 *
 * @return
 */
static inline int list_empty(struct list *head)
{
	return (head->next == head);
}

#endif /* __LIB_LIST_H__ */
