package core.logic

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction


object Works : Table() {
    val workId = varchar("workId", 100)
    val customer = text("customer")

    override val primaryKey = PrimaryKey(workId)
}

object Orders : Table() {
    val workId = varchar("workId", 100)
    val customerId = varchar("customerId", 100)
    val executorId = varchar("executorId", 100)
    val order = text("order")
    val report = text("report").nullable()

    override val primaryKey = PrimaryKey(workId, customerId, executorId)
}

object Workers : Table() {
    val workId = varchar("workId", 100)
    val userId = varchar("userId", 100)
    val worker = text("worker")

    override val primaryKey = PrimaryKey(workId, userId)
}


class DataStorageSqlImpl<WorkResults>(
    url: String,
    driver: String,
    user: String,
    password: String,
    private val workResultsSerializer: Serializer<WorkResults>
) : DataStorage<WorkResults> {

    private val database = Database.connect(url, driver, user, password)

    init {
        transaction(database) {
            SchemaUtils.create(Works, Orders, Workers)
        }
    }

    override fun addWork(workId: WorkId, customer: Customer) {
        transaction(database) {
            val maybeExistWork = Works.select {
                Works.workId eq workId
            }
            if (!maybeExistWork.empty()) {
                throw DispatcherError.WorkAlreadyExists
            }
            Works.insert {
                it[Works.workId] = workId
                it[Works.customer] = Json.encodeToString(customer)
            }
        }
    }


    override fun deleteWork(workId: WorkId) {
        transaction(database)  {
            val value = Works.select {
                Works.workId eq workId
            }
            if (value.empty()) {
                throw DispatcherError.WorkNotFound
            } else {
                Works.deleteWhere {
                    Works.workId eq workId
                }
                Orders.deleteWhere {
                    Orders.workId eq workId
                }
                Workers.deleteWhere {
                    Workers.workId eq workId
                }
            }
        }
    }

    override fun addOrder(workId: WorkId, orderId: OrderId, order: Order) {
        transaction(database)  {
            val maybeExistOrder = Orders.select {
                (Orders.workId eq workId) and (Orders.customerId eq orderId.customerId) and (Orders.executorId eq orderId.executorId)
            }
            if (!maybeExistOrder.empty()) {
                throw DispatcherError.OrderAlreadyExists
            }
            Orders.insert {
                it[Orders.workId] = workId
                it[Orders.executorId] = orderId.executorId
                it[Orders.customerId] = orderId.customerId
                it[Orders.order] = Json.encodeToString(order)
                it[Orders.report] = null
            }
        }
    }

    override fun getOrder(workId: WorkId, orderId: OrderId): Order {
        return transaction(database)  {
            val orderJson = Orders.select {
                (Orders.workId eq workId) and (Orders.executorId eq orderId.executorId) and (Orders.customerId eq orderId.customerId)
            }.map {
                it[Orders.order]
            }
            if (orderJson.isEmpty()) {
                throw DispatcherError.OrderNotFound
            } else {
                Json.decodeFromString(orderJson[0])
            }
        }
    }

    override fun addWorker(workId: WorkId, worker: Worker) {
        transaction(database)  {
            val maybeExistWork = Works.select {
                Works.workId eq workId
            }
            val maybeExistWorker = Workers.select {
                (Workers.workId eq workId) and (Workers.userId eq worker.userId)
            }
            if (maybeExistWork.empty()) {
                throw DispatcherError.WorkNotFound
            }
            if (!maybeExistWorker.empty()) {
                throw DispatcherError.WorkerAlreadyExists
            }
            Workers.insert {
                it[Workers.workId] = workId
                it[Workers.userId] = worker.userId
                it[Workers.worker] = Json.encodeToString(worker)
            }
        }
    }

    override fun modifyWorker(workId: WorkId, worker: Worker) {
        transaction(database)  {
            val maybeExistWork = Works.select {
                Works.workId eq workId
            }
            val maybeExistWorker = Workers.select {
                (Workers.workId eq workId) and (Workers.userId eq worker.userId)
            }
            if (maybeExistWork.empty()) {
                throw DispatcherError.WorkNotFound
            }
            if (maybeExistWorker.empty()) {
                throw DispatcherError.WorkerNotFound
            }
            Workers.update({ (Workers.workId eq workId) and (Workers.userId eq worker.userId) }) {
                it[Workers.worker] = Json.encodeToString(worker)
            }
        }
    }

    override fun deleteWorker(workId: WorkId, workerId: UserId) {
        transaction(database)  {
            val maybeExistWork = Works.select {
                Works.workId eq workId
            }
            val maybeExistWorker = Workers.select {
                (Workers.workId eq workId) and (Workers.userId eq workerId)
            }
            if (maybeExistWork.empty()) {
                throw DispatcherError.WorkNotFound
            }
            if (maybeExistWorker.empty()) {
                throw DispatcherError.WorkerNotFound
            }
            Workers.deleteWhere {
                (Workers.workId eq workId) and (Workers.userId eq workerId)
            }
        }
    }

    override fun getWorker(workId: WorkId, workerId: UserId): Worker {
        return transaction(database)  {
            val maybeExistWork = Works.select {
                Works.workId eq workId
            }
            val maybeExistWorker = Workers.select {
                (Workers.workId eq workId) and (Workers.userId eq workerId)
            }
            if (maybeExistWork.empty()) {
                throw DispatcherError.WorkNotFound
            }
            if (maybeExistWorker.empty()) {
                throw DispatcherError.WorkerNotFound
            }
            Json.decodeFromString(maybeExistWorker.map { it[Workers.worker] }[0])
        }
    }

    override fun modifyCustomer(workId: WorkId, customer: Customer) {
        transaction(database)  {
            val work = Works.select {
                Works.workId eq workId
            }
            if (work.empty()) {
                throw DispatcherError.WorkNotFound
            }
            Works.update({ Works.workId eq workId }) {
                it[Works.customer] = Json.encodeToString(customer)
            }
        }
    }

    override fun getCustomer(workId: WorkId): Customer {
        return transaction(database)  {
            val customerJson = Works.select {
                Works.workId eq workId
            }.map {
                it[Works.customer]
            }
            if (customerJson.isEmpty()) {
                throw DispatcherError.WorkNotFound
            } else {
                Json.decodeFromString(customerJson[0])
            }
        }
    }

    override fun addWorkResult(workId: WorkId, orderId: OrderId, report: WorkResults?) {
        transaction(database)  {
            val maybeExistOrder = Orders.select {
                (Orders.workId eq workId) and (Orders.executorId eq orderId.executorId) and (Orders.customerId eq orderId.customerId)
            }
            if (maybeExistOrder.empty()) {
                throw DispatcherError.OrderNotFound
            }
            Orders.update({ (Orders.workId eq workId) and (Orders.executorId eq orderId.executorId) and (Orders.customerId eq orderId.customerId) }) {
                it[Orders.report] = report?.let { it1 -> workResultsSerializer.toJson(it1) }
            }
        }
    }

    override fun getWorkResult(workId: WorkId, orderId: OrderId): WorkResults? {
        return transaction(database)  {
            val reportJson = Orders.select {
                (Orders.workId eq workId) and (Orders.executorId eq orderId.executorId) and (Orders.customerId eq orderId.customerId)
            }.map {
                it[Orders.report]
            }
            if (reportJson.isEmpty()) {
                throw DispatcherError.OrderNotFound
            } else {
                val result = reportJson[0]
                if (result == null) null else workResultsSerializer.fromJson(result)
            }
        }
    }
}