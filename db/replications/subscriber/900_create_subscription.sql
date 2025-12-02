-- Підписуємося на публікацію мастера
CREATE SUBSCRIPTION my_travel_subscription
    CONNECTION 'host=db port=5432 dbname=traveler_api_db user=rep_user password=rep_password'
    PUBLICATION my_travel_publication;