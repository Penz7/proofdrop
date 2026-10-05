// Local-dev seed data (see docs/API.md). Idempotent: safe to run on every container start.
const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcryptjs');

const prisma = new PrismaClient();

const HUB = { lat: 10.7769, lng: 106.7009 };

async function main() {
  const dispatchHash = await bcrypt.hash('dispatch123', 10);
  const courierHash = await bcrypt.hash('courier123', 10);

  await prisma.user.upsert({
    where: { email: 'dispatcher@proofdrop.dev' },
    update: {},
    create: { email: 'dispatcher@proofdrop.dev', name: 'Điều phối viên', role: 'DISPATCHER', passwordHash: dispatchHash },
  });

  const courierNames = ['Nguyễn Văn An', 'Trần Thị Bình', 'Lê Minh Châu'];
  const couriers = [];
  for (let i = 0; i < courierNames.length; i++) {
    couriers.push(
      await prisma.user.upsert({
        where: { email: `courier${i + 1}@proofdrop.dev` },
        update: {},
        create: {
          email: `courier${i + 1}@proofdrop.dev`,
          name: courierNames[i],
          role: 'COURIER',
          passwordHash: courierHash,
        },
      }),
    );
  }

  const devices = [
    ['DEV-001', 'Zebra TC22 Scanner', 'SCANNER', 'ZT22-88341', 92, null],
    ['DEV-002', 'Body Cam BC-4', 'BODY_CAM', 'BC4-10022', 76, null],
    ['DEV-003', 'Label Printer ZQ220', 'PRINTER', 'ZQ2-55120', 64, null],
    ['DEV-004', 'E-Scooter 07', 'VEHICLE', 'VN-59X1-0707', 81, null],
    ['DEV-005', 'Body Cam BC-4', 'BODY_CAM', 'BC4-10023', 18, couriers[1].id],
    ['DEV-006', 'Zebra TC22 Scanner', 'SCANNER', 'ZT22-88342', 55, null],
  ];
  for (const [id, name, type, serial, batteryPct, holderId] of devices) {
    await prisma.device.upsert({
      where: { id },
      update: {},
      create: { id, name, type, serial, batteryPct, holderId, checkedOutAt: holderId ? new Date() : null },
    });
  }

  if ((await prisma.order.count()) === 0) {
    const now = Date.now();
    const c1 = couriers[0].id;
    const orders = [
      ['Nguyễn An', '0901 234 567', '12 Nguyễn Huệ, Q.1, TP.HCM', 10.7743, 106.7038, '2x Cà phê sữa đá', 'ASSIGNED', 'PD-BEACON-01', c1],
      ['Trần Bình', '0902 345 678', '45 Lê Lợi, Q.1, TP.HCM', 10.7726, 106.699, 'Hồ sơ hợp đồng (A4)', 'ASSIGNED', null, c1],
      ['Lê Chi', '0903 456 789', '88 Đồng Khởi, Q.1, TP.HCM', 10.7764, 106.7032, 'Laptop sửa chữa', 'PICKED_UP', 'PD-BEACON-01', c1],
      ['Phạm Dũng', '0904 567 890', '101 Hai Bà Trưng, Q.1, TP.HCM', 10.7805, 106.7012, 'Thuốc kê đơn', 'ASSIGNED', null, c1],
      ['Hoàng Em', '0905 678 901', '7 Pasteur, Q.1, TP.HCM', 10.779, 106.6955, '1x Bánh mì đặc biệt', 'CREATED', null, null],
      ['Võ Giang', '0906 789 012', '23 Lý Tự Trọng, Q.1, TP.HCM', HUB.lat + 0.003, HUB.lng - 0.002, 'Tài liệu ngân hàng', 'CREATED', null, null],
    ];
    // Sequential inserts keep codes PD-1001..PD-1006 in this order.
    for (let i = 0; i < orders.length; i++) {
      const [customerName, customerPhone, address, latitude, longitude, items, status, beaconId, courierId] = orders[i];
      await prisma.order.create({
        data: {
          customerName, customerPhone, address, latitude, longitude, items, status, beaconId, courierId,
          assignedAt: courierId ? new Date(now - (orders.length - i) * 120_000) : null,
        },
      });
    }
  }

  console.log('Seed complete');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
