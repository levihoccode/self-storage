alter table rental_orders drop constraint rental_orders_status_check;

alter table rental_orders add constraint rental_orders_status_check
    check (status in ('Pending', 'Deposited', 'Scheduled', 'InProgress', 'Canceled', 'Done'));
