create database if not exists Juby;
use Juby;


select *
from daily_price
where stock_code = '102110'
order by date asc;